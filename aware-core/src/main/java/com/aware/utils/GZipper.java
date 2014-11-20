package com.aware.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Created by denzil on 19/11/14.
 */
public class GZipper {
    public static byte[] zip(byte[] fat) {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream(fat.length);
        try{
            GZIPOutputStream gzipOut = new GZIPOutputStream(byteOut);
            gzipOut.write(fat);
            gzipOut.close();
        } catch ( IOException e ) {
            e.printStackTrace();
        }
        return byteOut.toByteArray();
    }

    public static byte[] unzip(byte[] slim) {
        ByteArrayInputStream byteIn = new ByteArrayInputStream(slim);
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        try {
            GZIPInputStream gzipIn = new GZIPInputStream(byteIn);
            for( int value = 0; value != -1; ) {
                value = gzipIn.read();
                if( value != -1 ) byteOut.write(value);
            }
            gzipIn.close();
        } catch (IOException e){
            e.printStackTrace();
        }
        return byteOut.toByteArray();
    }
}
