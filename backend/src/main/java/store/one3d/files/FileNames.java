package store.one3d.files;

import java.util.Optional;

/** The display name kept for an upload. It is only ever shown or used as a download name, never as a path. */
final class FileNames {

    static final int MAX_LENGTH = 200;

    private FileNames() {
    }

    /**
     * Keeps the last path segment, replaces anything but letters, digits,
     * space, dot, dash, underscore and parentheses with "_", and requires an
     * ".stl" ending. Empty if the result is not a usable STL file name.
     */
    static Optional<String> sanitize(String requested) {
        if (requested == null) {
            return Optional.empty();
        }
        String name = requested.substring(Math.max(requested.lastIndexOf('/'), requested.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[^A-Za-z0-9 ._()-]", "_").strip();
        if (name.length() > MAX_LENGTH || name.length() <= ".stl".length()
                || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".stl")) {
            return Optional.empty();
        }
        return Optional.of(name);
    }
}
