package dev.white.client.gui;

/** Implemented by every module menu (classic windows and the sidebar menu) so the key handling can treat them alike. */
public interface ModuleGui {
    /** True while the menu is waiting for a key to bind or a text box has focus (so Right Shift must not close it). */
    boolean isBinding();
}
