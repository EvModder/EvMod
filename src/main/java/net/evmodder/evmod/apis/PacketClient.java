package net.evmodder.evmod.apis;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;
import jdk.net.ExtendedSocketOptions;
import net.evmodder.EvLib.util.PacketCodec;

/**
 * EvMod-only stateful TCP/UDP transport for fully encoded packets produced by {@link RemoteServerSender}.
 * {@link PacketCodec} owns the shared wire-format operations; this class owns connections and request lifecycle.
 * Responses must begin with the same unsigned three-byte request ID passed to {@link #sendPacket},
 * followed by an authentication tag bound to the request; both are removed before callback delivery.
 * Legacy single-request transport reference: EvLib commit f6444eb0, PacketHelper.java.
 */
public final class PacketClient implements AutoCloseable{
	private static final Logger LOGGER = Logger.getLogger("EvMod-PacketClient");
	private static final int MAX_UDP_PACKET_SIZE = 65_507;
	private static final int MAX_TCP_FRAME_SIZE = 0xFFFF;
	private static final int UDP_SEND_BUFFER_SIZE = 256*1024;
	private static final int UDP_RECEIVE_BUFFER_SIZE = 1024*1024;
	private static final AtomicInteger THREAD_ID = new AtomicInteger();
	private static final ThreadPoolExecutor CALLBACK_EXECUTOR = new ThreadPoolExecutor(
			Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors())), Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors())),
			0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1024), task->{
				final Thread thread = new Thread(task, "PacketClient-callback-"+THREAD_ID.incrementAndGet());
				thread.setDaemon(true);
				return thread;
			}, new ThreadPoolExecutor.CallerRunsPolicy());
	private static final ThreadPoolExecutor SEND_EXECUTOR = new ThreadPoolExecutor(
			Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())), Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())),
			0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8192), task->{
				final Thread thread = new Thread(task, "PacketClient-send-"+THREAD_ID.incrementAndGet());
				thread.setDaemon(true);
				return thread;
			}, new ThreadPoolExecutor.CallerRunsPolicy());
	private static final ScheduledThreadPoolExecutor TIMEOUT_EXECUTOR = new ScheduledThreadPoolExecutor(1, task->{
		final Thread thread = new Thread(task, "PacketClient-timeout");
		thread.setDaemon(true);
		return thread;
	});
	static{TIMEOUT_EXECUTOR.setRemoveOnCancelPolicy(true);}

	private static final class PendingRequest{
		final Consumer<byte[]> callback;
		final byte[] authenticationContext;
		volatile ScheduledFuture<?> timeoutTask;
		PendingRequest(final Consumer<byte[]> callback, final byte[] authenticationContext){
			this.callback = callback;
			this.authenticationContext = authenticationContext;
		}
	}

	private final InetAddress address;
	private final int port;
	private final String authenticationKey;
	private final Object udpLock = new Object(), tcpLock = new Object();
	private volatile UDPChannel udpChannel;
	private volatile TCPChannel tcpChannel;
	private volatile boolean closed;

	public PacketClient(final InetAddress address, final int port, final String authenticationKey){
		if(address == null) throw new IllegalArgumentException("address cannot be null");
		if(port < 0 || port > 0xFFFF) throw new IllegalArgumentException("Invalid port: "+port);
		if(authenticationKey == null) throw new IllegalArgumentException("authenticationKey cannot be null");
		this.address = address;
		this.port = port;
		this.authenticationKey = authenticationKey;
	}

	private void dispatchCallback(final Consumer<byte[]> callback, final byte[] response){
		if(callback == null) return;
		CALLBACK_EXECUTOR.execute(()->{
			try{callback.accept(response);}
			catch(RuntimeException ex){LOGGER.warning("Packet callback failed: "+ex);}
		});
	}

	private PendingRequest register(final ConcurrentHashMap<Integer, PendingRequest> pendingRequests,
			final int requestId, final long timeout, final byte[] message, final Consumer<byte[]> callback){
		if(callback == null) return null;
		if(message.length < PacketCodec.AUTH_TAG_SIZE) throw new IllegalArgumentException("Request has no authentication tag");
		final byte[] authenticationContext = new byte[PacketCodec.AUTH_TAG_SIZE];
		System.arraycopy(message, message.length-PacketCodec.AUTH_TAG_SIZE,
				authenticationContext, 0, PacketCodec.AUTH_TAG_SIZE);
		final PendingRequest pending = new PendingRequest(callback, authenticationContext);
		if(pendingRequests.putIfAbsent(requestId, pending) != null){
			throw new IllegalStateException("Duplicate in-flight request id: "+requestId);
		}
		pending.timeoutTask = TIMEOUT_EXECUTOR.schedule(
				()->complete(pendingRequests, requestId, pending, null), timeout, TimeUnit.MILLISECONDS);
		if(pendingRequests.get(requestId) != pending) pending.timeoutTask.cancel(false);
		return pending;
	}

	private void complete(final ConcurrentHashMap<Integer, PendingRequest> pendingRequests,
			final int requestId, final PendingRequest pending, final byte[] response){
		if(pending == null || !pendingRequests.remove(requestId, pending)) return;
		final ScheduledFuture<?> timeoutTask = pending.timeoutTask;
		if(timeoutTask != null) timeoutTask.cancel(false);
		dispatchCallback(pending.callback, response);
	}

	private void receiveResponse(final ConcurrentHashMap<Integer, PendingRequest> pendingRequests,
			final byte[] response, final int responseLength){
		if(responseLength < 3+PacketCodec.AUTH_TAG_SIZE){
			LOGGER.warning("Discarding response without a request id (length="+responseLength+")");
			return;
		}
		final int requestId = PacketCodec.getUnsignedMedium(ByteBuffer.wrap(response, 0, 3));
		final PendingRequest pending = pendingRequests.get(requestId);
		if(pending == null) return; // Fire-and-forget acknowledgement, timeout, or duplicate UDP response.
		final int authenticatedLength = responseLength-PacketCodec.AUTH_TAG_SIZE;
		if(!PacketCodec.verifyAuthenticationTag(response, authenticatedLength,
				authenticationKey, pending.authenticationContext)){
			LOGGER.warning("Discarding response with invalid authentication tag");
			return;
		}
		final byte[] payload = new byte[authenticatedLength-3];
		System.arraycopy(response, 3, payload, 0, payload.length);
		complete(pendingRequests, requestId, pending, payload);
	}

	private void failAll(final ConcurrentHashMap<Integer, PendingRequest> pendingRequests){
		pendingRequests.forEach((requestId, pending)->complete(pendingRequests, requestId, pending, null));
	}

	private UDPChannel getUDPChannel() throws IOException{
		if(closed) throw new IOException("Packet client is closed");
		UDPChannel channel = udpChannel;
		if(channel != null && !channel.isClosed()) return channel;
		synchronized(udpLock){
			if(closed) throw new IOException("Packet client is closed");
			channel = udpChannel;
			if(channel == null || channel.isClosed()){
				channel = new UDPChannel();
				udpChannel = channel;
				channel.start();
			}
			return channel;
		}
	}

	private TCPChannel getTCPChannel(final long timeout) throws IOException{
		if(closed) throw new IOException("Packet client is closed");
		TCPChannel channel = tcpChannel;
		if(channel != null && !channel.isClosed()) return channel;
		synchronized(tcpLock){
			if(closed) throw new IOException("Packet client is closed");
			channel = tcpChannel;
			if(channel == null || channel.isClosed()){
				channel = new TCPChannel(timeout);
				tcpChannel = channel;
				channel.start();
			}
			return channel;
		}
	}

	public void sendPacket(final boolean udp, final long timeout, final int requestId,
			final byte[] message, final Consumer<byte[]> callback){
		if(timeout <= 0 || timeout > PacketCodec.MAX_REQUEST_TIMEOUT_MILLIS){
			throw new IllegalArgumentException("Packet timeout must be 1-"
					+PacketCodec.MAX_REQUEST_TIMEOUT_MILLIS+"ms: "+timeout);
		}
		if(message == null){
			dispatchCallback(callback, null);
			throw new IllegalArgumentException("message cannot be null");
		}
		if(requestId < 0 || requestId > PacketCodec.MAX_REQUEST_ID){
			dispatchCallback(callback, null);
			throw new IllegalArgumentException("Request id must be unsigned 24-bit: "+requestId);
		}
		if(udp && message.length > MAX_UDP_PACKET_SIZE){
			dispatchCallback(callback, null);
			throw new IllegalArgumentException("UDP packet is too large: "+message.length);
		}
		if(!udp && message.length > MAX_TCP_FRAME_SIZE){
			dispatchCallback(callback, null);
			throw new IllegalArgumentException("TCP frame is too large: "+message.length);
		}
		final long deadlineNanos = System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeout);
		SEND_EXECUTOR.execute(()->sendPacketNow(udp, deadlineNanos, requestId, message, callback));
	}

	private long remainingTimeoutMillis(final long deadlineNanos){
		final long remainingNanos = deadlineNanos-System.nanoTime();
		return remainingNanos <= 0 ? 0 : Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
	}

	private void sendPacketNow(final boolean udp, final long deadlineNanos, final int requestId,
			final byte[] message, final Consumer<byte[]> callback){
		if(closed){
			dispatchCallback(callback, null);
			return;
		}
		long remainingTimeout = remainingTimeoutMillis(deadlineNanos);
		if(remainingTimeout == 0){
			dispatchCallback(callback, null);
			return;
		}
		if(udp){
			UDPChannel channel = null;
			PendingRequest pending = null;
			try{
				channel = getUDPChannel();
				remainingTimeout = remainingTimeoutMillis(deadlineNanos);
				if(remainingTimeout == 0){dispatchCallback(callback, null); return;}
				pending = register(channel.pendingRequests, requestId, remainingTimeout, message, callback);
				channel.send(message);
			}
			catch(IOException | RuntimeException ex){
				if(channel != null && pending != null) complete(channel.pendingRequests, requestId, pending, null);
				else dispatchCallback(callback, null);
				LOGGER.warning("UDP send failed: "+ex);
			}
		}
		else{
			TCPChannel channel = null;
			PendingRequest pending = null;
			try{
				channel = getTCPChannel(remainingTimeout);
				remainingTimeout = remainingTimeoutMillis(deadlineNanos);
				if(remainingTimeout == 0){dispatchCallback(callback, null); return;}
				pending = register(channel.pendingRequests, requestId, remainingTimeout, message, callback);
				channel.send(message);
			}
			catch(IOException | RuntimeException ex){
				if(channel != null && pending != null){
					complete(channel.pendingRequests, requestId, pending, null);
					channel.close();
				}
				else{
					dispatchCallback(callback, null);
					if(channel != null) channel.close();
				}
				LOGGER.warning("TCP send failed: "+ex);
			}
		}
	}

	private final class UDPChannel{
		final DatagramSocket socket;
		final ConcurrentHashMap<Integer, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
		volatile boolean channelClosed;

		UDPChannel() throws IOException{
			socket = new DatagramSocket();
			socket.setBroadcast(false);
			socket.setTrafficClass(/*IPTOS_LOWDELAY=*/0x10);
			socket.setSendBufferSize(UDP_SEND_BUFFER_SIZE);
			socket.setReceiveBufferSize(UDP_RECEIVE_BUFFER_SIZE);
			try{socket.setOption(ExtendedSocketOptions.IP_DONTFRAGMENT, true);}
			catch(IOException | UnsupportedOperationException ex){LOGGER.fine("IP_DONTFRAGMENT unavailable: "+ex);}
			socket.connect(address, port);
		}
		boolean isClosed(){return channelClosed || socket.isClosed();}
		void start(){
			final Thread reader = new Thread(this::readResponses, "PacketClient-UDP-"+THREAD_ID.incrementAndGet());
			reader.setDaemon(true);
			reader.start();
		}
		void send(final byte[] message) throws IOException{
			synchronized(socket){socket.send(new DatagramPacket(message, message.length));}
		}
		void readResponses(){
			final byte[] response = new byte[MAX_UDP_PACKET_SIZE];
			final DatagramPacket packet = new DatagramPacket(response, response.length);
			try{
				while(!isClosed()){
					packet.setLength(response.length);
					socket.receive(packet);
					receiveResponse(pendingRequests, response, packet.getLength());
				}
			}
			catch(IOException ex){if(!isClosed()) LOGGER.warning("UDP receive failed: "+ex);}
			finally{close();}
		}
		void close(){
			if(channelClosed) return;
			channelClosed = true;
			socket.close();
			synchronized(udpLock){if(udpChannel == this) udpChannel = null;}
			failAll(pendingRequests);
		}
	}

	private static int readUnsignedShort(final InputStream in) throws IOException{
		final int high = in.read(), low = in.read();
		if((high | low) < 0) throw new EOFException("Stream ended while reading unsigned short");
		return high<<8 | low;
	}

	private final class TCPChannel{
		final Socket socket;
		final InputStream in;
		final OutputStream out;
		final Object writeLock = new Object();
		final ConcurrentHashMap<Integer, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
		volatile boolean channelClosed;

		TCPChannel(final long timeout) throws IOException{
			socket = new Socket();
			socket.setPerformancePreferences(2, 1, 0);
			socket.setTrafficClass(/*IPTOS_LOWDELAY=*/0x10);
			socket.setTcpNoDelay(true);
			final int connectTimeout = (int)timeout;
			socket.connect(new InetSocketAddress(address, port), connectTimeout);
			in = socket.getInputStream();
			out = socket.getOutputStream();
		}
		boolean isClosed(){return channelClosed || socket.isClosed();}
		void start(){
			final Thread reader = new Thread(this::readResponses, "PacketClient-TCP-"+THREAD_ID.incrementAndGet());
			reader.setDaemon(true);
			reader.start();
		}
		void send(final byte[] message) throws IOException{
			synchronized(writeLock){
				PacketCodec.writeFrame(out, message);
				out.flush();
			}
		}
		void readResponses(){
			try{
				while(!isClosed()){
					final int responseLength = readUnsignedShort(in);
					if(responseLength < 3+PacketCodec.AUTH_TAG_SIZE){
						throw new IOException("TCP response is missing its request id or tag");
					}
					final byte[] response = in.readNBytes(responseLength);
					if(response.length != responseLength){
						throw new EOFException("TCP response ended before its declared length");
					}
					receiveResponse(pendingRequests, response, response.length);
				}
			}
			catch(IOException ex){if(!isClosed()) LOGGER.warning("TCP receive failed: "+ex);}
			finally{close();}
		}
		void close(){
			if(channelClosed) return;
			channelClosed = true;
			try{socket.close();}catch(IOException ignored){}
			synchronized(tcpLock){if(tcpChannel == this) tcpChannel = null;}
			failAll(pendingRequests);
		}
	}

	@Override public void close(){
		if(closed) return;
		closed = true;
		synchronized(udpLock){
			final UDPChannel udp = udpChannel;
			if(udp != null) udp.close();
		}
		synchronized(tcpLock){
			final TCPChannel tcp = tcpChannel;
			if(tcp != null) tcp.close();
		}
	}
}