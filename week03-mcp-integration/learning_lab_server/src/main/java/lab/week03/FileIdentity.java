package lab.week03;

import com.sun.jna.*;
import com.sun.jna.win32.StdCallLibrary;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HexFormat;

/** Platform adapter for identity; the edit/undo policy does not depend on an OS API. */
final class FileIdentity {
    private static final boolean WINDOWS=Platform.isWindows();
    private FileIdentity() {}
    private interface WindowsFiles extends StdCallLibrary {
        WindowsFiles API=Native.load("kernel32",WindowsFiles.class);
        Pointer CreateFileW(WString name,int access,int share,Pointer security,int creation,int flags,Pointer template);
        boolean GetFileInformationByHandleEx(Pointer handle,int informationClass,Pointer output,int size);
        int GetFileAttributesW(WString name);
        boolean CloseHandle(Pointer handle);
    }
    static boolean isReparsePoint(Path path) throws IOException {
        if(!WINDOWS) return false;
        int attributes=WindowsFiles.API.GetFileAttributesW(new WString(path.toString()));
        if(attributes==-1) throw new IOException("Cannot inspect storage attributes.");
        return (attributes & 0x400)!=0;
    }
    static String key(Path path,BasicFileAttributes attributes) throws IOException {
        if(attributes.fileKey()!=null) return attributes.fileKey().toString();
        if(!WINDOWS) throw new IOException("File identity is unavailable on this filesystem.");
        Pointer handle=WindowsFiles.API.CreateFileW(new WString(path.toString()),0,7,null,3,0x02200000,null);
        if(handle==null || Pointer.nativeValue(handle)==-1) throw new IOException("Cannot open file identity.");
        try(var data=new Memory(24)) {
            if(!WindowsFiles.API.GetFileInformationByHandleEx(handle,18,data,24))
                throw new IOException("Cannot read stable file identity.");
            return HexFormat.of().formatHex(data.getByteArray(0,24));
        } finally { WindowsFiles.API.CloseHandle(handle); }
    }
}
