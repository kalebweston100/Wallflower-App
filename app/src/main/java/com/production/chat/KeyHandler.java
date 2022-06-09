package com.production.chat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

//handle key storage and operations in connection
public class KeyHandler {

    //encrypt decrypt keys for this device
    private RSAPrivateKey privateDecrypt = null;
    private RSAPublicKey publicEncrypt = null;
    //encrypt key from peer
    private RSAPublicKey peerPublicEncrypt = null;

    //build encryption key pair
    public KeyHandler() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            privateDecrypt = (RSAPrivateKey) keyPair.getPrivate();
            publicEncrypt = (RSAPublicKey) keyPair.getPublic();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public byte[] encryptMessage(Message message) {
        byte[] encrypted = null;
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            //To use SHA-256 for both digests
            cipher.init(Cipher.ENCRYPT_MODE, peerPublicEncrypt, new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
            encrypted = cipher.doFinal(StandardCharsets.UTF_8.encode(message.getText()).array());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return encrypted;
    }

    public Message decryptMessage(ByteBuffer byteBuffer) {
        String decrypted = null;
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            //To use SHA-256 for both digests
            cipher.init(Cipher.DECRYPT_MODE, privateDecrypt, new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
            decrypted = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(cipher.doFinal(byteBuffer.array()))).toString();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return new Message(decrypted);
    }

    public byte[] getPublicEncrypt() {
        return publicEncrypt.getEncoded();
    }

    public void setPeerPublicEncrypt(ByteBuffer byteBuffer) {
        try {
            peerPublicEncrypt = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(byteBuffer.array()));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean needPeerPublic() {
        return peerPublicEncrypt == null;
    }
}
