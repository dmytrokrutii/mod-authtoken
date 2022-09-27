package org.folio.auth.authtokenmodule;

import java.text.ParseException;
import com.nimbusds.jose.JOSEException;

import io.vertx.core.json.JsonObject;
import org.folio.auth.authtokenmodule.tokens.AccessToken;
import org.folio.auth.authtokenmodule.tokens.DummyToken;
import org.folio.auth.authtokenmodule.tokens.LegacyAccessToken;
import org.folio.auth.authtokenmodule.tokens.RefreshToken;
import org.folio.auth.authtokenmodule.tokens.Token;
import org.folio.auth.authtokenmodule.tokens.TokenValidationContext;
import org.folio.auth.authtokenmodule.tokens.TokenValidationException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import io.vertx.core.Future;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

public class TokenTest {
  private static String userUUID = "007d31d2-1441-4291-9bb8-d6e2c20e399a";
  private static String passPhrase = "CorrectBatteryHorseStaple";
  private static String tenant = "test-abc";
  private static String username = "test-username";

  @Before
  public void setSigningKey() {
    System.setProperty("jwt.signing.key", passPhrase);
  }

  @Test
  public void accessTokenIsValidTest() throws JOSEException, ParseException, TokenValidationException {
    var at = new AccessToken(tenant, username, userUUID);
    String key = System.getProperty("jwt.signing.key");
    var tokenCreator = new TokenCreator(key);
    var encoded = at.encodeAsJWT(tokenCreator);

    var context = new TokenValidationContext(null, tokenCreator, encoded);
    Future<Token> result = Token.validate(context);

    // Access token validation will never have async operations within their validation.
    assertThat(result.succeeded(), is(true));
    assertThat(result.result(), is(instanceOf(AccessToken.class)));
  }

  @Test
  public void accessTokenIsInvalidTest() throws JOSEException, ParseException, TokenValidationException {
    String tokenMissingTenantClaim =
      "{\"iat\":1637696002,\"sub\":\"test-username\",\"user_id\":\"007d31d2-1441-4291-9bb8-d6e2c20e399a\",\"type\":\"access\"}";
    String key = System.getProperty("jwt.signing.key");
    var tokenCreator = new TokenCreator(key);
    String source = tokenCreator.createJWTToken(tokenMissingTenantClaim);

    var context = new TokenValidationContext(null, tokenCreator, source);
    Future<Token> result = Token.validate(context);

    // Access tokens will never have async operations within their validation.
    assertThat(result.failed(), is(true));
    assertThat(result.cause(), is(instanceOf(TokenValidationException.class)));
    assertThat(((TokenValidationException) result.cause()).getHttpResponseCode(), is(500));
  }

  @Test
  public void tokenIsEncryptedTest() throws TokenValidationException, JOSEException, ParseException {
    String key = System.getProperty("jwt.signing.key");
    var tokenCreator = new TokenCreator(key);
    String unencryptedToken =
      new AccessToken("test-tenant", "username-1", "userid-1").encodeAsJWT(tokenCreator);
    String encryptedToken =
      new RefreshToken("test-tenant", "username-1", "userid-1", "http://localhost").encodeAsJWE(tokenCreator);

    assertThat(Token.isEncrypted(encryptedToken), is(true));
    assertThat(Token.isEncrypted(unencryptedToken), is(false));
  }

  @Test
  public void noTypeDummy() throws TokenValidationException {
    JsonObject claims = new JsonObject()
      .put("dummy", true)
      .put("tenant", "lib");
    Token parse = Token.parse(".", claims);
    assertThat(parse instanceof DummyToken, is(true));
  }

  @Test
  public void noTypeLegacyAccess() throws TokenValidationException {
    JsonObject claims = new JsonObject()
      .put("tenant", "lib");
    Token parse = Token.parse(".", claims);
    assertThat(parse instanceof LegacyAccessToken, is(true));
  }

  @Test
  public void badTypeString() {
    JsonObject claims = new JsonObject()
      .put("type", "foo")
      .put("tenant", "lib");
    Throwable t = Assert.assertThrows(TokenValidationException.class, () -> Token.parse(".", claims));
    assertThat(t.getMessage(), is("Unable to parse token"));
  }

  @Test
  public void badTypeStructure() {
    JsonObject claims = new JsonObject()
      .put("type", 3)
      .put("tenant", "lib");
    Throwable t = Assert.assertThrows(TokenValidationException.class, () -> Token.parse(".", claims));
    assertThat(t.getMessage(), is("Unable to parse token"));
  }
}
