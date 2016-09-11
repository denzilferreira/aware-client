
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
     *                      that only a known hash_function is passed. (MD5, SHA-1, SHA-256,
     *                      SHA-384, SHA-512).q
     * @return String
     */
    public static final String hashGeneric(String clear, String hash_function) {
        if( clear == null ) return "";
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
        }
        return "";
    }

    /**
     * Hash a string.  This method finds the right hash functions from settings (setting
     * "hash_function") and calls the right hash.  It handles backwards compatibility:
     * defaults to SHA-1, and also defaults to SHA-1 if an invalid algorithm name is given.
     *
     * @param context Application context (for getting settings)
     * @param clear Text to be hashed
     * @return Hex-encoded hash
     */
    public static final String hash(Context context, String clear) {
        if( clear == null ) return "";
        String HASH_FUNCTION = Aware.getSetting(context.getApplicationContext(), Aware_Preferences.HASH_FUNCTION);
        String HASH_SALT = Aware.getSetting(context.getApplicationContext(), Aware_Preferences.HASH_SALT);
        // Option to salt per-device.
        if (HASH_SALT.equals("device_id"))
            HASH_SALT = Aware.getSetting(context, Aware_Preferences.DEVICE_ID);
        // HASH_SALT defaults to empty
        clear = clear + HASH_SALT;

        // Go through and find our hash function, and apply it.  Handle defaults to SHA-1.
        // Currently testing for each value individually to ensure a proper value is passed
        // (or else exception raised)
        if (HASH_FUNCTION.equals("")) {
            // Default if unset
            return hashSHA1(clear);
        } else if (HASH_FUNCTION.equals("SHA-1")) {
            return hashSHA1(clear);
        } else if (HASH_FUNCTION.equals("SHA-256")) {
            // Remember to be careful to only allow allowed names here.
            return hashGeneric(clear, HASH_FUNCTION);
        } else if (HASH_FUNCTION.equals("SHA-512")) {
            return hashGeneric(clear, HASH_FUNCTION);
        } else if (HASH_FUNCTION.equals("MD5")) {
            return hashMD5(clear);
        } else {
            return hashGeneric(clear, "SHA-1");
        }
    }

    /**
     * Hash a phone number.  This considers the setting "hash_function_phone" and can treat
     * the number specially.  Options are "normalize": remove all characters except "+" and
     * 0-9 from the number so that hashes can be compared.  "last6": only hashes the last six
     * characters.  "salt_deviceid": Salt using our device_id.  Note that HASH_SALT is also
     * applied!
     *
     * @param context Application context (for getting settings)
     * @param clear Text to hash
     * @return Hex-encoded hash
     */
    public static final String hashPhone(Context context, String clear) {
        String HASH_FUNCTION_PHONE = Aware.getSetting(context.getApplicationContext(), Aware_Preferences.HASH_FUNCTION_PHONE);

        if (HASH_FUNCTION_PHONE.equals("normalize")) {
            // Remove everything except 0-9 and "+"
            clear = clear.replaceAll("[^\\d+]", "");
            return hash(context, clear);
        } else if (HASH_FUNCTION_PHONE.equals("last6")) {
            // Hash only last six digits characters
            clear = clear.replaceAll("[^\\d+]", "");
            // Find the last six
            int start_idx = clear.length() - 6;
            if (start_idx < 0)
                start_idx = 0;
            clear = clear.substring(start_idx);
            return hash(context, clear);
        } else if (HASH_FUNCTION_PHONE.equals("salt_deviceid")) {
            // Salt using our device ID.  Note that if HASH_SALT is also applied!
            clear = clear.replaceAll("[^\\d+]", "");
            clear = clear + Aware.getSetting(context, Aware_Preferences.DEVICE_ID);
            return hash(context, clear);
        }
        else {
            return hash(context, clear);
        }
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