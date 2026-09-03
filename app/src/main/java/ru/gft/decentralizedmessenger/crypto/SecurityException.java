package ru.gft.decentralizedmessenger.crypto;

/** Mirrors api.utils.Encryption.SecurityError. */
public class SecurityException extends RuntimeException {
    public SecurityException(String msg) { super(msg); }
}
