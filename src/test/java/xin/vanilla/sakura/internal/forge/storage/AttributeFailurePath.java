package xin.vanilla.sakura.internal.forge.storage;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.function.Predicate;

/** Real files with a provider that reports selected metadata reads as denied. */
final class AttributeFailurePath implements Path {
    private final Path delegate;
    private final FaultSystem system;

    static Path deny(Path path, Predicate<Path> blocked) {
        return new AttributeFailurePath(path, new FaultSystem(path.getFileSystem(), blocked));
    }
    private AttributeFailurePath(Path path, FaultSystem system) { this.delegate = path; this.system = system; }
    private Path wrap(Path path) { return path == null ? null : new AttributeFailurePath(path, system); }
    private static Path unwrap(Path path) {
        return path instanceof AttributeFailurePath ? ((AttributeFailurePath) path).delegate : path;
    }
    public FileSystem getFileSystem() { return system; }
    public boolean isAbsolute() { return delegate.isAbsolute(); }
    public Path getRoot() { return wrap(delegate.getRoot()); }
    public Path getFileName() { return wrap(delegate.getFileName()); }
    public Path getParent() { return wrap(delegate.getParent()); }
    public int getNameCount() { return delegate.getNameCount(); }
    public Path getName(int index) { return wrap(delegate.getName(index)); }
    public Path subpath(int begin, int end) { return wrap(delegate.subpath(begin, end)); }
    public boolean startsWith(Path other) { return delegate.startsWith(unwrap(other)); }
    public boolean startsWith(String other) { return delegate.startsWith(other); }
    public boolean endsWith(Path other) { return delegate.endsWith(unwrap(other)); }
    public boolean endsWith(String other) { return delegate.endsWith(other); }
    public Path normalize() { return wrap(delegate.normalize()); }
    public Path resolve(Path other) { return wrap(delegate.resolve(unwrap(other))); }
    public Path resolve(String other) { return wrap(delegate.resolve(other)); }
    public Path resolveSibling(Path other) { return wrap(delegate.resolveSibling(unwrap(other))); }
    public Path resolveSibling(String other) { return wrap(delegate.resolveSibling(other)); }
    public Path relativize(Path other) { return wrap(delegate.relativize(unwrap(other))); }
    public URI toUri() { return delegate.toUri(); }
    public Path toAbsolutePath() { return wrap(delegate.toAbsolutePath()); }
    public Path toRealPath(LinkOption... options) throws IOException { return wrap(delegate.toRealPath(options)); }
    public File toFile() { return delegate.toFile(); }
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
            throws IOException { return delegate.register(watcher, events, modifiers); }
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
        return delegate.register(watcher, events);
    }
    public Iterator<Path> iterator() {
        List<Path> result = new ArrayList<>(); delegate.forEach(value -> result.add(wrap(value))); return result.iterator();
    }
    public int compareTo(Path other) { return delegate.compareTo(unwrap(other)); }
    public String toString() { return delegate.toString(); }
    public int hashCode() { return delegate.hashCode(); }
    public boolean equals(Object other) {
        return other instanceof AttributeFailurePath && system == ((AttributeFailurePath) other).system
                && delegate.equals(((AttributeFailurePath) other).delegate);
    }

    private static final class FaultSystem extends FileSystem {
        final FileSystem delegate;
        final FileSystemProvider provider;
        FaultSystem(FileSystem delegate, Predicate<Path> blocked) {
            this.delegate = delegate; this.provider = new FaultProvider(delegate.provider(), blocked);
        }
        public FileSystemProvider provider() { return provider; }
        public void close() { }
        public boolean isOpen() { return true; }
        public boolean isReadOnly() { return delegate.isReadOnly(); }
        public String getSeparator() { return delegate.getSeparator(); }
        public Iterable<Path> getRootDirectories() { return delegate.getRootDirectories(); }
        public Iterable<FileStore> getFileStores() { return delegate.getFileStores(); }
        public Set<String> supportedFileAttributeViews() { return delegate.supportedFileAttributeViews(); }
        public Path getPath(String first, String... more) { return new AttributeFailurePath(delegate.getPath(first, more), this); }
        public PathMatcher getPathMatcher(String expression) { return delegate.getPathMatcher(expression); }
        public UserPrincipalLookupService getUserPrincipalLookupService() { return delegate.getUserPrincipalLookupService(); }
        public WatchService newWatchService() throws IOException { return delegate.newWatchService(); }
    }

    private static final class FaultProvider extends FileSystemProvider {
        final FileSystemProvider delegate;
        final Predicate<Path> blocked;
        FaultProvider(FileSystemProvider delegate, Predicate<Path> blocked) { this.delegate = delegate; this.blocked = blocked; }
        private void check(Path path) throws IOException {
            if (blocked.test(unwrap(path))) throw new AccessDeniedException(path.toString());
        }
        public String getScheme() { return "metadata-fault"; }
        public FileSystem newFileSystem(URI uri, Map<String, ?> environment) { throw new UnsupportedOperationException(); }
        public FileSystem getFileSystem(URI uri) { throw new UnsupportedOperationException(); }
        public Path getPath(URI uri) { throw new UnsupportedOperationException(); }
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
                throws IOException { return delegate.newByteChannel(unwrap(path), options, attrs); }
        public java.nio.channels.FileChannel newFileChannel(Path path, Set<? extends OpenOption> options,
                                                           FileAttribute<?>... attrs) throws IOException {
            return delegate.newFileChannel(unwrap(path), options, attrs);
        }
        public DirectoryStream<Path> newDirectoryStream(Path path, DirectoryStream.Filter<? super Path> filter)
                throws IOException { return delegate.newDirectoryStream(unwrap(path), filter); }
        public void createDirectory(Path path, FileAttribute<?>... attrs) throws IOException { delegate.createDirectory(unwrap(path), attrs); }
        public void delete(Path path) throws IOException { delegate.delete(unwrap(path)); }
        public void copy(Path from, Path to, CopyOption... options) throws IOException { delegate.copy(unwrap(from), unwrap(to), options); }
        public void move(Path from, Path to, CopyOption... options) throws IOException { delegate.move(unwrap(from), unwrap(to), options); }
        public boolean isSameFile(Path one, Path two) throws IOException { return delegate.isSameFile(unwrap(one), unwrap(two)); }
        public boolean isHidden(Path path) throws IOException { return delegate.isHidden(unwrap(path)); }
        public FileStore getFileStore(Path path) throws IOException { return delegate.getFileStore(unwrap(path)); }
        public void checkAccess(Path path, AccessMode... modes) throws IOException { check(path); delegate.checkAccess(unwrap(path), modes); }
        public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
            return delegate.getFileAttributeView(unwrap(path), type, options);
        }
        public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
                throws IOException { check(path); return delegate.readAttributes(unwrap(path), type, options); }
        public Map<String, Object> readAttributes(Path path, String attrs, LinkOption... options)
                throws IOException { check(path); return delegate.readAttributes(unwrap(path), attrs, options); }
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options)
                throws IOException { delegate.setAttribute(unwrap(path), attribute, value, options); }
    }
}
