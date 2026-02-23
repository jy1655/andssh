package com.opencode.sshterminal.terminal;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

final class BridgeTerminalSessionClient implements TerminalSessionClient {
    private final Runnable onTextChanged;
    private final Runnable onBellReceived;

    BridgeTerminalSessionClient(Runnable onTextChanged, Runnable onBellReceived) {
        this.onTextChanged = onTextChanged;
        this.onBellReceived = onBellReceived;
    }

    @Override
    public void onTextChanged(TerminalSession changedSession) {
        onTextChanged.run();
    }

    @Override
    public void onTitleChanged(TerminalSession changedSession) {}

    @Override
    public void onSessionFinished(TerminalSession finishedSession) {}

    @Override
    public void onCopyTextToClipboard(TerminalSession session, String text) {}

    @Override
    public void onPasteTextFromClipboard(TerminalSession session) {}

    @Override
    public void onBell(TerminalSession session) {
        onBellReceived.run();
    }

    @Override
    public void onColorsChanged(TerminalSession session) {}

    @Override
    public void onTerminalCursorStateChange(boolean state) {}

    @Override
    public Integer getTerminalCursorStyle() {
        return TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE;
    }

    @Override
    public void logError(String tag, String message) {}

    @Override
    public void logWarn(String tag, String message) {}

    @Override
    public void logInfo(String tag, String message) {}

    @Override
    public void logDebug(String tag, String message) {}

    @Override
    public void logVerbose(String tag, String message) {}

    @Override
    public void logStackTraceWithMessage(String tag, String message, Exception e) {}

    @Override
    public void logStackTrace(String tag, Exception e) {}
}
