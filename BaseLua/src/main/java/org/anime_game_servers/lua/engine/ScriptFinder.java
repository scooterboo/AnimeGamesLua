package org.anime_game_servers.lua.engine;

import java.io.InputStream;
import java.nio.file.Path;

public interface ScriptFinder {
    InputStream openScript(String scriptName);
    Path getScriptPath(String scriptName);
}
