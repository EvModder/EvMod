package net.evmodder.EvLib;

import java.util.HashMap;
import java.util.function.Consumer;

public abstract class LoadingCache<K, V>{
	private final HashMap<K, V> cache;
	//TODO: loading duration tracking?
	private final HashMap<K, Thread> loading = new HashMap<>();
	private final V V_NOT_FOUND, V_LOADING;
	boolean alwaysLoadSync;
	public LoadingCache(final HashMap<K, V> initMap, final V notFound, final V loading){cache = initMap; V_NOT_FOUND = notFound; V_LOADING = loading;}
	public LoadingCache(final V notFound, final V loading){this(new HashMap<>(), notFound, loading);}
	public LoadingCache(){this(new HashMap<>(), null, null);}

	protected abstract V load(final K k);
	protected V loadSyncOrNull(final K k){return null;}
	public final V remove(final K k){return cache.remove(k);}
	public final int size(){return cache.size();}
	public final boolean contains(final K k){return cache.containsKey(k);}

	public final boolean putIfAbsent(final K k, final V v){
		synchronized(cache){
			if(cache.containsKey(k) && cache.get(k) != V_NOT_FOUND) return false;
			cache.put(k, v);
		}
		final Thread t;
		synchronized(loading){t = loading.remove(k);}
		if(t != null) t.interrupt();
		return true;
	}
//	public final boolean containsKey(final K k){return cache.containsKey(k);}
	private final void loadValue(final K k, final Consumer<V> callback){
		synchronized(loading){
			if(!loading.containsKey(k)){
				final Thread t = new Thread(()->{
					final V v = load(k);
					if(v == null ? V_LOADING == null : v.equals(V_LOADING)){
						synchronized(loading){if(loading.containsKey(k)) loading.put(k, null);} // null out this Thread so it can be garbage collected
						return; // Assume the caller will call putIfAbsent() once a value is loaded
					}
					synchronized(cache){cache.put(k, v);}
					synchronized(loading){loading.remove(k);}
					if(callback != null) callback.accept(v);
				});
				loading.put(k, t);
				t.start();
			}
		}
	}
	public final V get(final K k, final Consumer<V> callback){
		{
			final V v = loadSyncOrNull(k);
			if(v != null){
				assert v != V_LOADING : "loadSyncOrNull() failed to do what its name implies (didn't load, yet didn't return null)";
				cache.put(k, v);
				if(callback != null) callback.accept(v);
				return v;
			}
		}
		if(cache.containsKey(k)){
			if(callback != null) callback.accept(cache.get(k));
			return cache.get(k);
//			final V v = cache.get(k);
//			if(v == null) assert V_NOT_FOUND == null;
//			return v != null ? v : V_NOT_FOUND;
		}
		loadValue(k, callback);
		return V_LOADING;
	}
	public final V getSync(final K k){
		{
			final V v = loadSyncOrNull(k);
			if(v != null){
				assert v != V_LOADING : "loadSyncOrNull() failed to do what its name implies (didn't load, yet didn't return null";
				cache.put(k, v);
				return v;
			}
		}
		if(cache.containsKey(k)) return cache.get(k);
		final boolean iAmTheLoader;
//		assert Thread.currentThread() != null; // iirc, main thread id=1
		synchronized(loading){iAmTheLoader = loading.putIfAbsent(k, Thread.currentThread()) == null;}
		if(iAmTheLoader){
			final V v = load(k);
			if(!v.equals(V_LOADING)){
				synchronized(cache){cache.put(k, v);}
				synchronized(loading){loading.remove(k);}
				return v;
			}
		}
		else while(loading.containsKey(k)) Thread.yield();
		// Sometimes even load() itself is async/callback driven and not guaranteed to give a value...
		while(!cache.containsKey(k)) Thread.yield(); // So we just wait for someone to call putIfAbsent()
		return cache.get(k);
	}
}