package com.github.silasw.owltwitter;

public class AttributeWatch {
	/**
	 * Identifier of the object.
	 */
	String uri;
	/**
	 * Name of the attribute to be watched.
	 */
	String attname;
	/**
	 * Boolean value of the previously observed data.
	 */
	boolean prev;
	/**
	 *  1 if waiting for true, -1 if waiting for false, 0 if waiting for any change.
	 */
	int param;
	AttributeWatch(String newuri, String name, String watchtype){
		uri = newuri;
		attname = name;
		// If the watch type is not recognized as "true" or "false", default to watching for any change.
		if("true".equals(watchtype)){
			param = 1;
			prev = false;
		}
		else if("false".equals(watchtype)){
			param = -1;
			prev = true;
		}
		else
			param = 0;
	}
}
