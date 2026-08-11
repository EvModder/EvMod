package net.evmodder.evmod.apis;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.evmodder.EvLib.util.Command;
import net.evmodder.EvLib.util.PacketCodec;
import net.evmodder.EvLib.util.ServerIdRequest;

public final class RemoteServerSender{
	public static final int DEFAULT_PORT = 14441;
	private static final long RESOLVE_RETRY_NS = 5_000l * 1000000l;
	private static final long SERVER_ID_TIMEOUT = 5_000l;
	private static final ScheduledThreadPoolExecutor SERVER_ID_TIMEOUT_EXECUTOR = new ScheduledThreadPoolExecutor(1, task->{
		final Thread thread = new Thread(task, "EvMod-server-id-timeout");
		thread.setDaemon(true);
		return thread;
	});
	static{SERVER_ID_TIMEOUT_EXECUTOR.setRemoveOnCancelPolicy(true);}
	public record ServerDescriptor(String address, String name, String endpoint, Integer knownServerId, boolean singleplayer){}

	private final Logger LOGGER;
	private final Supplier<ServerDescriptor> CURRENT_SERVER;
	private final AtomicInteger nextRequestId = new AtomicInteger();
	private final ConcurrentHashMap<String, Integer> serverIds = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, CompletableFuture<Integer>> pendingServerIds = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> serverIdRetryAfterNs = new ConcurrentHashMap<>();
	private volatile ServerDescriptor activeServer;
	private volatile boolean disconnectingFromMinecraftServer;

	private record ConnectionState(String remoteAddress, int port, InetAddress resolvedAddress,
			PacketClient packetClient, int clientId, String clientKey){}
	private volatile ConnectionState connectionState;
	private long nextResolveAttemptNs;

	private InetAddress resolveAddress(final String remoteAddress){
		if(remoteAddress == null) return null;
		try{return InetAddress.getByName(remoteAddress);}
		catch(UnknownHostException ex){
			LOGGER.warn("Server not found: "+remoteAddress);
			return null;
		}
	}

	public final synchronized void setConnectionDetails(final String addr, final int port, final int clientId, final String clientKey){
		if(port < 0 || port > 0xFFFF) throw new IllegalArgumentException("Invalid port: "+port);
		if(clientKey == null) throw new IllegalArgumentException("clientKey cannot be null");
		final InetAddress resolved = resolveAddress(addr);
		final ConnectionState previous = connectionState;
		final boolean sameConfiguredEndpoint = previous != null && previous.port() == port
				&& (addr == null ? previous.remoteAddress() == null : addr.equals(previous.remoteAddress()));
		final InetAddress effectiveAddress = resolved != null ? resolved
				: sameConfiguredEndpoint ? previous.resolvedAddress() : null;
		final PacketClient packetClient = previous != null && previous.packetClient() != null
				&& previous.port() == port && clientKey.equals(previous.clientKey())
				&& effectiveAddress != null && effectiveAddress.equals(previous.resolvedAddress())
				? previous.packetClient() : effectiveAddress == null ? null : new PacketClient(effectiveAddress, port, clientKey);
		connectionState = new ConnectionState(addr, port, effectiveAddress, packetClient, clientId, clientKey);
		nextResolveAttemptNs = resolved == null && addr != null ? System.nanoTime()+RESOLVE_RETRY_NS : 0;
		if(previous != null && previous.packetClient() != null && previous.packetClient() != packetClient)
			previous.packetClient().close();
	}

	private synchronized ConnectionState retryUnresolvedAddress(final ConnectionState expected){
		if(connectionState != expected) return connectionState;
		if(expected == null || expected.packetClient() != null || expected.remoteAddress() == null) return expected;
		final long now = System.nanoTime();
		if(now - nextResolveAttemptNs < 0) return expected;
		nextResolveAttemptNs = now+RESOLVE_RETRY_NS;
		final InetAddress resolved = resolveAddress(expected.remoteAddress());
		if(resolved == null) return expected;
		final ConnectionState resolvedState = new ConnectionState(expected.remoteAddress(), expected.port(), resolved,
				new PacketClient(resolved, expected.port(), expected.clientKey()), expected.clientId(), expected.clientKey());
		connectionState = resolvedState;
		nextResolveAttemptNs = 0;
		return resolvedState;
	}

	private ConnectionState currentConnectionState(){
		final ConnectionState current = connectionState;
		return current == null || current.packetClient() != null ? current : retryUnresolvedAddress(current);
	}

	private synchronized void refreshAddressAfterFailure(final ConnectionState failedState){
		if(connectionState != failedState || failedState.remoteAddress() == null) return;
		final long now = System.nanoTime();
		if(now - nextResolveAttemptNs < 0) return;
		nextResolveAttemptNs = now+RESOLVE_RETRY_NS;
		final InetAddress resolved = resolveAddress(failedState.remoteAddress());
		if(resolved == null || resolved.equals(failedState.resolvedAddress())) return;
		final PacketClient replacement = new PacketClient(resolved, failedState.port(), failedState.clientKey());
		connectionState = new ConnectionState(failedState.remoteAddress(), failedState.port(), resolved, replacement,
				failedState.clientId(), failedState.clientKey());
		nextResolveAttemptNs = 0;
		failedState.packetClient().close();
	}

	public RemoteServerSender(final Logger logger, final Supplier<ServerDescriptor> serverGetter){
		LOGGER = logger;
		CURRENT_SERVER = serverGetter;
	}
	public final void minecraftServerConnected(){activeServer = CURRENT_SERVER.get(); disconnectingFromMinecraftServer = false;}
	public final void minecraftServerDisconnecting(){
		final ServerDescriptor current = CURRENT_SERVER.get();
		if(current != null) activeServer = current;
		disconnectingFromMinecraftServer = true;
	}
	public final void minecraftServerDisconnected(){activeServer = null; disconnectingFromMinecraftServer = false;}

	// Returns a `4+message.length+16+AUTH_TAG_SIZE`-byte packet
	private byte[] packageAndEncryptMessage(final int requestId, final Command command, final byte[/*16*n*/] message,
			final int clientId, final String clientKey, final int serverId){
		if((message.length&15) != 0) throw new IllegalArgumentException("Command body must be a multiple of 16 bytes: "+message.length);
		final ByteBuffer bb1 = ByteBuffer.allocate(16+message.length);
		bb1.putInt(clientId);
		bb1.putInt(serverId);
		bb1.putInt((int)System.currentTimeMillis());
		PacketCodec.putUnsignedMedium(bb1, requestId);
		if(command.ordinal() > 0xFF) throw new IllegalArgumentException("Command id exceeds one byte: "+command);
		bb1.put((byte)command.ordinal());
		bb1.put(message);
		final byte[] encryptedMessage = PacketCodec.encrypt(bb1.array(), clientKey);
		if(encryptedMessage == null) throw new IllegalStateException("Failed to encrypt remote-server request");

		final ByteBuffer bb2 = ByteBuffer.allocate(Integer.BYTES+encryptedMessage.length+PacketCodec.AUTH_TAG_SIZE);
		bb2.putInt(clientId);
		bb2.put(encryptedMessage);
		final byte[] packet = bb2.array();
		PacketCodec.writeAuthenticationTag(packet, Integer.BYTES+encryptedMessage.length, clientKey, /*context=*/null);
		return packet;
	}

	private final String formatTimeMillis(final long latency){
		// Output: 2s734ms
//		return TextUtils.formatTime(latency, false, "", 2, new long[]{1000, 1}, new char[]{'s', ' '}).stripTrailing()+"ms";
		// Output: 2734ms
		return latency+"ms";
	}
	private void sendMessage(final ConnectionState state, final int serverId, final Command command, final boolean udp,
			final long timeout, final byte[] message, final Consumer<byte[]> recv){
		if(timeout <= 0){
			if(recv != null) recv.accept(null);
			return;
		}
		final int requestId = nextRequestId.getAndUpdate(id->id == PacketCodec.MAX_REQUEST_ID ? 0 : id+1);
		final byte[] packet = packageAndEncryptMessage(requestId, command, message, state.clientId(), state.clientKey(), serverId);
		LOGGER.debug("RMS: sending "+(udp?"UDP":"TCP")+" cmd="+command+", serverId="
				+Integer.toUnsignedString(serverId)+", requestId="+requestId+", len="+packet.length);
		if(recv == null) state.packetClient().sendPacket(udp, timeout, requestId, packet, /*callback=*/null);
		else{
			final long startNs = System.nanoTime();
			state.packetClient().sendPacket(udp, timeout, requestId, packet, reply->{
				final long latency = (System.nanoTime()-startNs)/1000000l;
				if(reply == null){
					LOGGER.info("RemoteServerSender "+(udp?"UDP":"TCP")+" request "+requestId+" timed out or failed");
					refreshAddressAfterFailure(state);
				}
				else LOGGER.debug("RMS: got "+(udp?"UDP":"TCP")+" reply for request "+requestId
						+" (in "+formatTimeMillis(latency)+") [len="+reply.length+"]");
				recv.accept(reply);
			});
		}
	}

	private String serverCacheKey(final ConnectionState state, final ServerDescriptor server){
		return state.remoteAddress().strip().toLowerCase(Locale.ROOT)+':'+state.port()+'\0'+state.clientId()+'\0'
				+server.address().strip().toLowerCase(Locale.ROOT);
	}
	private CompletableFuture<Integer> resolveServerId(final ConnectionState state, final ServerDescriptor server){
		if(server.address() == null || server.address().isBlank()) return CompletableFuture.completedFuture(null);
		final String cacheKey = serverCacheKey(state, server);
		final Integer cached = serverIds.get(cacheKey);
		if(cached != null) return CompletableFuture.completedFuture(cached);
		final Long retryAfterNs = serverIdRetryAfterNs.get(cacheKey);
		if(retryAfterNs != null){
			if(System.nanoTime()-retryAfterNs < 0) return CompletableFuture.completedFuture(null);
			serverIdRetryAfterNs.remove(cacheKey, retryAfterNs);
		}
		final CompletableFuture<Integer> created = new CompletableFuture<>();
		final CompletableFuture<Integer> existing = pendingServerIds.putIfAbsent(cacheKey, created);
		if(existing != null) return existing;
		final Integer raced = serverIds.get(cacheKey);
		if(raced != null){
			pendingServerIds.remove(cacheKey, created);
			created.complete(raced);
			return created;
		}
		final byte[] request;
		try{request = ServerIdRequest.encode(server.address(), server.name(), server.endpoint());}
		catch(IllegalArgumentException ex){
			pendingServerIds.remove(cacheKey, created);
			created.completeExceptionally(ex);
			return created;
		}
		try{
			sendMessage(state, /*serverId=*/0, Command.RESOLVE_SERVER_ID, /*udp=*/false, SERVER_ID_TIMEOUT, request, reply->{
				Integer serverId = null;
				if(reply != null && reply.length == Integer.BYTES){
					serverId = ByteBuffer.wrap(reply).getInt();
					if(serverId == 0) serverId = null;
				}
				if(serverId == null){
					serverIdRetryAfterNs.put(cacheKey, System.nanoTime()+RESOLVE_RETRY_NS);
					LOGGER.warn("RMS was unable to resolve a server ID for "+server.address());
				}
				else{
					serverIdRetryAfterNs.remove(cacheKey);
					serverIds.put(cacheKey, serverId);
					LOGGER.info("RMS resolved "+server.address()+" to server ID "+Integer.toUnsignedString(serverId));
				}
				pendingServerIds.remove(cacheKey, created);
				created.complete(serverId);
			});
		}
		catch(RuntimeException ex){
			pendingServerIds.remove(cacheKey, created);
			created.completeExceptionally(ex);
			throw ex;
		}
		return created;
	}

	private void sendAfterServerId(final ConnectionState state, final ServerDescriptor server, final Command command,
			final boolean udp, final long timeout, final byte[] message, final Consumer<byte[]> recv){
		final long deadlineNs = System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeout);
		final AtomicBoolean completed = new AtomicBoolean();
		final CompletableFuture<Integer> resolution = resolveServerId(state, server);
		final ScheduledFuture<?> timeoutTask = recv != null && !resolution.isDone()
				? SERVER_ID_TIMEOUT_EXECUTOR.schedule(()->{
					if(completed.compareAndSet(false, true)) recv.accept(null);
				}, timeout, TimeUnit.MILLISECONDS) : null;
		resolution.whenComplete((serverId, error)->{
			if(timeoutTask != null) timeoutTask.cancel(false);
			if(!completed.compareAndSet(false, true)) return;
			final long remainingNs = deadlineNs-System.nanoTime();
			if(error != null || serverId == null || remainingNs <= 0){
				if(error != null) LOGGER.error("RMS server-ID resolution failed", error);
				if(recv != null) recv.accept(null);
				return;
			}
			final long remainingMillis = Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNs));
			sendMessage(state, serverId, command, udp, remainingMillis, message, recv);
		});
	}

	public final void sendBotMessage(final Command command, final boolean udp, final long timeout, final byte[] message, final Consumer<byte[]> recv){
		if(timeout <= 0 || timeout > PacketCodec.MAX_REQUEST_TIMEOUT_MILLIS){
			throw new IllegalArgumentException("RMS timeout must be 1-"+PacketCodec.MAX_REQUEST_TIMEOUT_MILLIS+"ms: "+timeout);
		}
		if(command.sendsResponse() != (recv != null)){
			throw new IllegalArgumentException(command+" sendsResponse="+command.sendsResponse()+", callback="+(recv != null));
		}
		ServerDescriptor server = null;
		if(command.requiresServerId()){
			server = CURRENT_SERVER.get();
			if(server == null && disconnectingFromMinecraftServer) server = activeServer;
			if(server == null || server.singleplayer()){
				LOGGER.debug("RMS: suppressing "+command+" because no multiplayer server is active");
				if(recv != null) recv.accept(null);
				return;
			}
		}
		final ConnectionState state = currentConnectionState();
		if(state == null || state.packetClient() == null){
			LOGGER.warn("RemoteSender address could not be resolved!: "+(state == null ? null : state.remoteAddress()));
			if(recv != null) recv.accept(null);
			return;
		}
		if(!command.requiresServerId()){
			sendMessage(state, /*serverId=*/0, command, udp, timeout, message, recv);
			return;
		}
		if(server.knownServerId() != null){
			sendMessage(state, server.knownServerId(), command, udp, timeout, message, recv);
			return;
		}
		sendAfterServerId(state, server, command, udp, timeout, message, recv);
	}

	public final static void main(String... args) throws IOException{
		UUID pearlUUID = UUID.fromString("a8c5dd6e-5f95-4875-9494-7c1d519ba8c8");
		UUID ownerUUID = UUID.fromString("34471e8d-d0c5-47b9-b8e1-b5b9472affa4");
//		UUID loc = new UUID(Double.doubleToRawLongBits(x), Double.doubleToRawLongBits(z));

		RemoteServerSender rss = new RemoteServerSender(LoggerFactory.getLogger("RMS"),
				()->new ServerDescriptor("2b2t.org", "2b2t", null, "2b2t.org".hashCode(), false));
		rss.setConnectionDetails("localhost", DEFAULT_PORT, 1, "some_unique_key");

		byte[] storePearlOwnerMsg = ByteBuffer.allocate(32)
				.putLong(pearlUUID.getMostSignificantBits()).putLong(pearlUUID.getLeastSignificantBits())
				.putLong(ownerUUID.getMostSignificantBits()).putLong(ownerUUID.getLeastSignificantBits()).array();
		rss.sendBotMessage(Command.DB_PEARL_STORE_BY_UUID, /*udp=*/true, /*timeout=*/5000, storePearlOwnerMsg,
				reply->System.out.println("store response: "+(reply == null ? null : reply[0])));

		rss.sendBotMessage(Command.DB_PEARL_FETCH_BY_UUID, /*udp=*/true, /*timeout=*/5000, PacketCodec.toByteArray(pearlUUID), msg->{
			if(msg == null){System.err.println("Expected msg non-null !");return;}
			if(msg.length != 16){System.err.println("Expected msg size == 16 !!!");return;}
			ByteBuffer byteBuffer = ByteBuffer.wrap(msg);
			long high = byteBuffer.getLong();
			long low = byteBuffer.getLong();
			UUID fetchedOwnerUUID = new UUID(high, low);
			System.out.println("owner uuid: "+fetchedOwnerUUID.toString());
		});
	}
}