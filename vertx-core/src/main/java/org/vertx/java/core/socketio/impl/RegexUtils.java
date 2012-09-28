package org.vertx.java.core.socketio.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Keesun Baik
 */
public class RegexUtils {

	public static String[] match(String input, String regexp) {
		Matcher matcher = Pattern.compile(regexp).matcher(input);
		String[] results = new String[matcher.groupCount() + 1];

		while (matcher.find()) {
			for(int i = 0 ; i < results.length ; i++) {
				results[i] = matcher.group(i);
			}
		}

		return results;
	}
}
