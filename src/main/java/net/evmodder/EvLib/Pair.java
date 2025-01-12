package net.evmodder.EvLib;

public class Pair<T,R>{
	public final T a; public R b;
	public Pair(T t, R r){a=t; b=r;}

	@Override public boolean equals(Object p){
		return p != null && p instanceof Pair && a.equals(((Pair<?, ?>)p).a) && b.equals(((Pair<?, ?>)p).b);
	}
	@Override public int hashCode(){
		return (a == null ? 0 : a.hashCode()) + (b == null ? 0 : b.hashCode());
	}
	@Override public String toString(){
		return a.toString()+","+b.toString();
	}
}