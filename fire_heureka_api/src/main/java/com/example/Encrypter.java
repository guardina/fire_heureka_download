package com.example;

import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class Encrypter {

    public void runTest() {
        String text = "My secret text";
        System.out.println(text);

        String encryptText = encrypt(text);
        System.out.println(encryptText);

        System.out.println(decrypt(encryptText));
    }

    public String encrypt(String token) {
        try {
            String encodedKey = System.getenv("AES_KEY");
            byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
            SecretKeySpec secretKey = new SecretKeySpec(decodedKey, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedToken = cipher.doFinal(token.getBytes());
            return Base64.getEncoder().encodeToString(encryptedToken);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            return "";
        }
        
    }


    public String decrypt(String encryptedToken) {
        try {
            String encodedKey = System.getenv("AES_KEY");
            byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
            SecretKeySpec secretKeySpec = new SecretKeySpec(decodedKey, "AES");

            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);

            byte[] decodedBytes = Base64.getDecoder().decode(encryptedToken);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);

            return new String(decryptedBytes);
        } catch (Exception e) {
            System.out.println("Decryption failed: " + e.getMessage());
            return null;
        }
    }

    public void generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey secretKey = keyGen.generateKey();
            
            String encodedKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());
            
            System.out.println("Generated AES Key (Base64): " + encodedKey);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
       
    }
    
}
