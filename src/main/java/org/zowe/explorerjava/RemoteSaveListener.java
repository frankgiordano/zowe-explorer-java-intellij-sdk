package org.zowe.explorerjava;

import com.intellij.util.messages.Topic;

/**
 * Project-level notification used by the tool window status line after remote saves.
 */
@FunctionalInterface
public interface RemoteSaveListener {

    Topic<RemoteSaveListener> TOPIC =
            Topic.create("Remote Save", RemoteSaveListener.class);

    void remoteSaved(String target);
}
