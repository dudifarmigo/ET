/**
 * Created by Dudi on 5/1/2016.
 */
public class ExactTarget implements IFtpSettings {
    String server;
    String user;
    String pass;
    int port;

    public ExactTarget(String server, String user, String pass, int port) {
        this.server = server;
        this.user = user;
        this.pass = pass;
        this.port = port;
    }

    public String getServer() {
        return server;
    }

    public String getUser() {
        return user;
    }

    public String getPass() {
        return pass;
    }

    public int getPort() {
        return port;
    }

}
