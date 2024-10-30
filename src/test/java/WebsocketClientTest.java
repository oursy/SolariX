import com.github.solarix.websocket.WebsocketClient;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class WebsocketClientTest {

  private static WebsocketClient websocketClient;

  @BeforeAll
  public static void setUp() throws URISyntaxException, SSLException {
    // Initialize a new EventLoopGroup for each test if needed
    websocketClient = new WebsocketClient("wss://api.mainnet-beta.solana.com");
  }

  @AfterAll
  public static void tearDown() {
    if (websocketClient != null) {
      websocketClient.close();
    }
  }

  @Test
  public void testConnect() throws InterruptedException {
    System.out.println("ws connect");
    websocketClient.handshake();
    System.out.println("close channel");
    WebsocketClient.getChannel().close().sync();
  }

  @Test
  public void testReConnect() throws InterruptedException {
    // set idle timeout params
    websocketClient.setReaderIdleTimeSeconds(2);
    websocketClient.setWriterIdleTimeSeconds(3);
    websocketClient.handshake();
    // mock reade timeout
    TimeUnit.SECONDS.sleep(5);
    System.out.println("reconnect");
    System.out.println("close channel");
    WebsocketClient.getChannel().close().sync();
  }
}
