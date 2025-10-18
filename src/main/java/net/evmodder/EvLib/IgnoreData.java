package net.evmodder.EvLib;

import java.util.HashSet;
import java.util.UUID;

//public record IgnoredBy(int submitterId, long created){}
//public record IgnoreData(UUID ignored, List<IgnoredBy> by){}
public record IgnoreData(HashSet<UUID> ignored, int submitterId, long submittedTs){

//	@Override public boolean equals(Object o){
//		if(this == o) return true;
//		if(o == null || getClass() != o.getClass()) return false;
//		return ignored.equals(((IgnoreData)o).ignored());
//	}
//	@Override public int hashCode(){return ignored.hashCode();}
}