import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.LocalDate;
import java.util.Base64;

/**
 * Generates one ES256 (EC P-256) private signing key as a JWK, using only the JDK.
 *
 *   java scripts/GenerateJwk.java                 # kid = identity-YYYY-MM-DD
 *   java scripts/GenerateJwk.java my-key-id > my-key-id.json
 *
 * The output is a SECRET. Store it in your secret manager / mounted secret directory, never in Git.
 */
public class GenerateJwk {

    public static void main(String[] args) throws Exception {
        String kid = args.length > 0 ? args[0] : "identity-" + LocalDate.now();
        if (!kid.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("kid may only contain letters, digits, '.', '_' and '-'");
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        ECPublicKey pub = (ECPublicKey) pair.getPublic();
        ECPrivateKey priv = (ECPrivateKey) pair.getPrivate();

        System.out.println("{\"kty\":\"EC\",\"crv\":\"P-256\",\"use\":\"sig\",\"alg\":\"ES256\""
                + ",\"kid\":\"" + kid + "\""
                + ",\"x\":\"" + b64(pub.getW().getAffineX()) + "\""
                + ",\"y\":\"" + b64(pub.getW().getAffineY()) + "\""
                + ",\"d\":\"" + b64(priv.getS()) + "\"}");
    }

    /** Unsigned, big-endian, left-padded to 32 bytes, base64url without padding (RFC 7518 section 6.2). */
    private static String b64(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] fixed = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, fixed, 32 - copy, copy);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(fixed);
    }
}
