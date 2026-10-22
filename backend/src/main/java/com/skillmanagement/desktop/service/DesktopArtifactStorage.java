package com.skillmanagement.desktop.service;

import com.skillmanagement.desktop.model.DesktopPackage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

@Service
@Profile({"desktop-pilot","desktop-test"})
public class DesktopArtifactStorage {
    private final String configuredRoot;
    public DesktopArtifactStorage(@Value("${desktop.pilot.package-root:}") String root) { configuredRoot=root; }
    public InputStreamResource open(DesktopPackage artifact) {
        FileChannel channel=null;
        try {
            if(configuredRoot.isBlank())throw unavailable();
            Path root=Path.of(configuredRoot).toAbsolutePath().normalize().toRealPath();
            Path path=root.resolve(artifact.storageKey()+".pkg");
            var attributes=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!attributes.isRegularFile() || attributes.isSymbolicLink())throw unavailable();
            Set<OpenOption> options=new HashSet<>(Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS));
            if(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")) {
                options.add(com.sun.nio.file.ExtendedOpenOption.NOSHARE_WRITE);
                options.add(com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE);
            }
            channel=FileChannel.open(path,options);
            if(channel.size()!=artifact.sizeBytes() || artifact.sizeBytes()>104857600)throw unavailable();
            var digest=MessageDigest.getInstance("SHA-256");
            var buffer=ByteBuffer.allocate(65536);long count=0;
            while(channel.read(buffer)!=-1) {
                count+=buffer.position();if(count>artifact.sizeBytes())throw unavailable();
                digest.update(buffer.array(),0,buffer.position());buffer.clear();
            }
            if(count!=artifact.sizeBytes() || !HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(artifact.sha256()))throw unavailable();
            channel.position(0);
            // The response converter closes this stream; keep the verified file handle
            // through streaming, including Windows deny-write/delete sharing.
            var resource=new InputStreamResource(Channels.newInputStream(channel));channel=null;
            return resource;
        } catch(NoSuchFileException missing) { throw new DesktopFailure(404,"PACKAGE_NOT_FOUND","The published package file was not found."); }
        catch(DesktopFailure failure) { throw failure; }
        catch(Exception failure) { throw unavailable(); }
        finally { if(channel!=null)try{channel.close();}catch(java.io.IOException ignored){} }
    }
    private DesktopFailure unavailable() { return new DesktopFailure(503,"PACKAGE_UNAVAILABLE","The published package bytes cannot be verified."); }
}
