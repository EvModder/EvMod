package net.evmodder.EvLib;

public class Tuple3<T extends Comparable<T>, R extends Comparable<R>, S extends Comparable<S>>
implements Comparable<Tuple3<T, R, S>>{
	public final T a; public final R b; public final S c;
	public Tuple3(T t, R r, S s){a=t; b=r; c=s;}
	@Override public boolean equals(Object p){
		return p != null && p instanceof Tuple3 &&
				a.equals(((Tuple3<?, ?, ?>)p).a) && b.equals(((Tuple3<?, ?, ?>)p).b) && c.equals(((Tuple3<?, ?, ?>)p).c);
	}
	@Override public int hashCode(){
		return a.hashCode() ^ b.hashCode() ^ c.hashCode();
	}

	@Override public int compareTo(Tuple3<T, R, S> o){
		int aComp = a.compareTo(o.a);
		if(aComp != 0) return aComp;
		int bComp = b.compareTo(o.b);
		return bComp != 0 ? bComp : c.compareTo(o.c);
	}

	@Override public String toString(){
		return new StringBuilder().append(a).append(',').append(b).append(',').append(c).toString();
	}
}