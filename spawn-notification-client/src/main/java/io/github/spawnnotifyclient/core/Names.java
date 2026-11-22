package io.github.spawnnotifyclient.core;

import java.util.Locale;

/** Normalisation so that "Mr. Mime", "mr_mime", "cobblemon:mr_mime" and "MRMIME" all compare equal. */
public final class Names {
	private Names() {}

	public static String norm(String raw) {
		if (raw == null) {
			return "";
		}
		String s = raw.trim().toLowerCase(Locale.ROOT);
		int colon = s.indexOf(':');
		if (colon >= 0) {
			s = s.substring(colon + 1);
		}
		StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
				out.append(c);
			}
		}
		return out.toString();
	}
}
