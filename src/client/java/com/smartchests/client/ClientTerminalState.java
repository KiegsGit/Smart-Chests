package com.smartchests.client;

import com.smartchests.DataPayload;

/** Latest data from the server. The screen polls it, so packet / screen creation order never matters. */
final class ClientTerminalState {
	static DataPayload latest;
	static int version;

	static void accept(DataPayload payload) {
		latest = payload;
		version++;
	}

	static void reset() {
		latest = null;
	}

	private ClientTerminalState() {
	}
}
