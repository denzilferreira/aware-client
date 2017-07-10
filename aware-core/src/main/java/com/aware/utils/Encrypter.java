
package com.aware.utils;

import android.content.Context;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts/decrypts strings using a seed 
 * @author Denzil Ferreira
 *
 */
public class Encrypter {
	/**
	 * Encrypts a string given a seed
	 * @param seed
	 * @param cleartext
	 * @return
	 * @throws Exception
	 */
    public static String encrypt(String seed, String cleartext) throws Exception {
        byte[] rawKey = getRawKey(seed.getBytes());
        byte[] result = encrypt(rawKey, cleartext.getBytes());
        return toHex(result);
    }
    
    /**
     * Decrypts a string given a seed
     * @param seed
     * @param encrypted
     * @return
     * @throws Exception
     */
    public static String decrypt(String seed, String encrypted) throws Exception {
        byte[] rawKey = getRawKey(seed.getBytes());
        byte[] enc = toByte(encrypted);
        byte[] result = decrypt(rawKey, enc);
        return new String(result);
    }
    
    /**
     * One-way string hashing using SHA1
     * @param clear string
     * @return encrypted string
     * @throws NoSuchAlgorithmException
     */
    public static String hashSHA1( String clear ) {
        // Stub, no longer needed
        return hashGeneric(clear, "SHA-1");
    }
    
    /**
     * One-way string hashing using MD5
     * @param clear
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static final String hashMD5(String clear) {
        // Stub, no longer needed
        return hashGeneric(clear, "MD5");
    }

    /**
     * Hash a string with a given algorithm
     *
     * One-way string hashing using any algorithm
     * @param clear Text to be hashed
     * @param hash_function One of the allowed Android hash functions.  You sould be very sure
     *                      that only a known hash_function is passed.
     *                      (MD5, SHA-1, SHA-256, SHA-384, SHA-512).
     * @return String
     */
    public static final String hashGeneric(String clear, String hash_function) {
        if( clear == null || clear.length() == 0 ) return "";
        try {
            // Create Hash
            MessageDigest digest = java.security.MessageDigest.getInstance(hash_function);
            digest.update(clear.getBytes());
            byte messageDigest[] = digest.digest();

            // Create Hex String
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < messageDigest.length; i++) {
                String h = Integer.toHexString(0xFF & messageDigest[i]);
                while (h.length() < 2)
                    h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "<invalid alg "+hash_function+">";
        }
    }

    /*
     * Run a hash program to hash data.  This not only does a hash, but can salt the cleartext,
     * normalize the data, or apply other transformations.
     *
     * This looks up the settingName and runs it as a hash program.  It splits it by commas and
     * runs these commands:
     * - Anything not below: hash using that hash name (hashGeneric).  Ends processing.
     * - "true": hash using the global HASH_FUNCTION and HASH_SALT.  Ends processing.
     * - "clear": return the cleartext (default for some modes)
     * - "salt=XXXX": salt using this value
     * - "salt=device_id": salt using the device_id
     * - "last=N": take only the last N digits
     * - "normalize": Remove all non [0-9+] characters.
     */
    public static final String _hashProgram(Context context, String clear, String hashProgram) {
        if( clear == null || clear.length() == 0  ) return "";

        if (hashProgram.length() > 0) {
            String[] hashProgramCommands = hashProgram.split(",");
            for (String command: hashProgramCommands) {
                if (command.equals("salt=device_id")) {
                    // Salt using the device_id
                    clear = clear + Aware.getSetting(context, Aware_Preferences.DEVICE_ID);
                } else if (command.startsWith("salt=")) {
                    // Salt using any string
                    String[] command_split = command.split("=");
                    // If salting with a empty hash, do nothing.
                    if (command_split.length == 1) continue;
                    clear = clear + command_split[1];
                } else if (command.equals("normalize")) {
                    // Remove all characters not in [0-9+]
                    clear = clear.replaceAll("[^\\d+]", "");
                } else if (command.equals("normalizeAlnum")) {
                    // Remove all characters not in [0-9A-Za-z+]
                    clear = clear.replaceAll("[^\\dA-Za-z+]", "");
                } else if (command.startsWith("last=")) {
                    // Take only the last N digits
                    int start_idx = clear.length() - Integer.parseInt(command.split("=")[1]);
                    if (start_idx < 0)
                        start_idx = 0;
                    clear = clear.substring(start_idx);
                } else if (command.equals("clear")) {
                    // Do not hash.  This is default for some modes.
                    return clear;
                } else if (command.equals("true")) {
                    // Hash using global settings
                    return hash(context, clear);
                } else {
                    // This is a hash algorithm name.  Hash it and return.  Stops loop.
                    return hashGeneric(clear, command);
                }
            }
        }
        // Default: hash with default parameters (actually sets default parameters and calls
        // this function again) if there are no
        return hash(context, clear);
    }

    /**
     * Hash a string.  This method finds the right hash functions from settings (setting
     * "hash_function") and calls the right hash.  It handles backwards compatibility:
     * defaults to SHA-1, and also defaults to SHA-1 if an invalid algorithm name is given.
     *
     * This performs the default hashing.  The actual work is desegated to _hashProgram().
     *
     * @param context Application context (for getting settings)
     * @param clear Text to be hashed
     * @return Hex-encoded hash
     */
    public static final String hash(Context context, String clear) {
        String hashProgram = Aware.getSetting(context.getApplicationContext(), Aware_Preferences.HASH_FUNCTION);
        if (hashProgram.equals("")) {
            // Default if unset
            hashProgram = "SHA-1";
        }

        String HASH_SALT = Aware.getSetting(context.getApplicationContext(), Aware_Preferences.HASH_SALT);
        // Option to salt per-device.
        if (HASH_SALT.equals("device_id"))
            hashProgram = "salt=device_id," + hashProgram;
        // HASH_SALT defaults to empty
        hashProgram = "salt="+ HASH_SALT + "," + hashProgram;
        return _hashProgram(context, clear, hashProgram);
    }

    /*
     * Hash a phone number.  Default to hashing even if blank.
     */
    public static final String hashPhone(Context context, String clear) {
        String hashProgram = Aware.getSetting(context.getApplicationContext(), Aware_Preferences.HASH_FUNCTION_PHONE);
        return _hashProgram(context, clear, hashProgram);
    }

    /*
     * Hash a MAC address.  Defaults to not hashing.
     */
    public static final String hashMac(Context context, String clear) {
        String hashProgram = Aware.getSetting(context.getApplicationContext(), Aware_Preferences.HASH_FUNCTION_MAC);
        if (hashProgram.equals("")) {
            hashProgram = "clear";
        }
        return _hashProgram(context, clear, hashProgram);
    }

    /*
     * Hash a wifi/bluetooth SSID/name.  Defaults to not hashing.
     */
    public static final String hashSsid(Context context, String clear) {
        String hashProgram = Aware.getSetting(context.getApplicationContext(), Aware_Preferences.HASH_FUNCTION_SSID);
        if (hashProgram.equals("")) {
            hashProgram = "clear";
        }
        return _hashProgram(context, clear, hashProgram);
    }



    private static byte[] getRawKey(byte[] seed) throws Exception {
        KeyGenerator kgen = KeyGenerator.getInstance("AES");
        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        sr.setSeed(seed);
        kgen.init(128, sr); // 192 and 256 bits may not be available
        SecretKey skey = kgen.generateKey();
        byte[] raw = skey.getEncoded();
        return raw;
    }

    private static byte[] encrypt(byte[] raw, byte[] clear) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
        byte[] encrypted = cipher.doFinal(clear);
        return encrypted;
    }

    private static byte[] decrypt(byte[] raw, byte[] encrypted) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, skeySpec);
        byte[] decrypted = cipher.doFinal(encrypted);
        return decrypted;
    }

    public static String toHex(String txt) {
        return toHex(txt.getBytes());
    }
    
    public static String fromHex(String hex) {
        return new String(toByte(hex));
    }
    
    public static byte[] toByte(String hexString) {
        int len = hexString.length()/2;
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++)
            result[i] = Integer.valueOf(hexString.substring(2*i, 2*i+2), 16).byteValue();
        return result;
    }

    public static String toHex(byte[] buf) {
        if (buf == null)
            return "";
        StringBuffer result = new StringBuffer(2*buf.length);
        for (int i = 0; i < buf.length; i++) {
            appendHex(result, buf[i]);
        }
        return result.toString();
    }
    private final static String HEX = "0123456789ABCDEF";
    private static void appendHex(StringBuffer sb, byte b) {
        sb.append(HEX.charAt((b>>4)&0x0f)).append(HEX.charAt(b&0x0f));
    }
}