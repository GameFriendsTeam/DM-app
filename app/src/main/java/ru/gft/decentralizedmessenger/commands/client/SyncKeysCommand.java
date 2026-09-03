package ru.gft.decentralizedmessenger.commands.client;

import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import ru.gft.decentralizedmessenger.commands.Command;
import ru.gft.decentralizedmessenger.commands.CommandSender;

/** Mirrors api.commands.client.SyncKeysCMD.SyncKeysCMD. */
public class SyncKeysCommand extends Command {
    @Override
    public void execute(CommandSender cs) {
        String getter = cs.getContext().currentGetter;
        var enc = cs.getEncript(getter);
        if (enc != null) {
            var trusted = enc.getTrustedPeerKey(getter);
            if (trusted != null) {
                System.out.println("No verification needed");
                if (trusted.x25519Public != null && trusted.x25519Public.length > 0) {
                    enc.generateKeypair();
                    enc.deriveSharedKey(new X25519PublicKeyParameters(trusted.x25519Public, 0));
                    System.out.println("Secure channel restored!");
                }
                return;
            }
        }
        System.out.println("Sending key...");
        cs.sendKey(getter);
        System.out.println("Reading key packet...");
        cs.readKey(getter, 6);
    }
}
