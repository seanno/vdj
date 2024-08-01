//
// REPERTOIRESPEC.JAVA
//

package com.shutdownhook.vdj.vdjlib;

public class RepertoireSpec
{
	public RepertoireSpec() {
		this.UserId = null;
		this.Context = null;
		this.Name = null;
	}
	
	public RepertoireSpec(String userId, String context, String name) {
		this.UserId = userId;
		this.Context = context;
		this.Name = name;
	}

	public RepertoireSpec(RepertoireSpec source) {
		this.UserId = source.UserId;
		this.Context = source.Context;
		this.Name = source.Name;
	}

	public RepertoireSpec(RepertoireSpec source, RepertoireSpec fallback) {
		this.UserId = (source != null && source.UserId != null ? source.UserId : fallback.UserId);
		this.Context = (source != null && source.Context != null ? source.Context : fallback.Context);
		this.Name = (source != null && source.Name != null ? source.Name : fallback.Name);
	}
	
	public String toString() { return(String.format("%s/%s/%s", UserId, Context, Name)); }
		
	public String UserId;
	public String Context;
	public String Name;
}
	
