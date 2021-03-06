import com.google.common.collect.FluentIterable;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by Dudi on 5/1/2016.
 */
public class Utils {
    public final static int GS_DOWNLOAD_ET_TIME = 480;
    public final static int ET_MERGE_CSV_FILES_TIME = 30;

    public static boolean downloadFilesFromGsFolder(String gsFolder,
                                                    final String localFolderPath,
                                                    final String localNewFolderPath,
                                                    int downloadTime) throws Exception {
        boolean isDownloaded = false;

        Process process = null;
        if(isLinuxOS()){
            ProcessBuilder pb = new ProcessBuilder(ET_Const.GSUTIL_FOLDER_PATH,
                    "cp",
                    "-r",
                    gsFolder,
                    localFolderPath
            );
            pb.redirectErrorStream(true);
            process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null)
                System.out.println(line);

            process.waitFor();
            File folder = new File(localNewFolderPath);
            return folder.exists() && folder.listFiles().length > 0;

        }
        process = Runtime.getRuntime().exec("cmd /c start gsutil cp -r " + gsFolder + " " + localFolderPath);

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        isDownloaded = sync(downloadTime, 3, new SyncLoop() {
            long lastModified() throws Exception {
                File folder = new File(localNewFolderPath);
                System.out.println(folder.lastModified());
                return folder.lastModified();
            }
            boolean isModified() throws Exception {
                long lastModified = lastModified();
                Thread.sleep(10000);
                return lastModified < lastModified();

            }
            boolean isExist(){
                File folder = new File(localNewFolderPath);
                return folder.exists();
            }
            @Override
            public boolean check() throws Exception {
                return isExist() && !isModified();
            }
            @Override
            public String getErrorMessage() {
                return "Error: downloadFilesFromGsFolder() - time out";
            }
        });

        return isDownloaded;
    }

    public static boolean uploadFileToFtp(IFtpSettings iFtpSettings, String localFilePath, String remoteFileName) {
        boolean isUploaded = false;
        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(iFtpSettings.getServer(), iFtpSettings.getPort());
            ftpClient.login(iFtpSettings.getUser(), iFtpSettings.getPass());
            ftpClient.enterLocalPassiveMode();

            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            File localFile = new File(localFilePath);

            String firstRemoteFile = remoteFileName;
            InputStream inputStream = new FileInputStream(localFile);

            System.out.println("Start uploading file: " + new Date());
            isUploaded = ftpClient.storeFile(firstRemoteFile, inputStream);
            inputStream.close();
            if (isUploaded) {
                System.out.println("The file is uploaded successfully. " + new Date());
            }

        } catch (IOException ex) {
            System.out.println("Error: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return isUploaded;
    }

    public static boolean isLinuxOS(){
        return System.getProperty("os.name").equals("Linux");
    }

    public static boolean mergeCSVFiles(final String folderPath, String outputFileName, int mergeTime) throws Exception {
        boolean isMerged = false;
        if(isLinuxOS()){
            List<String> files = FluentIterable.from(getAllFilesInFolder(folderPath))
                    .filter(new com.google.common.base.Predicate<String>() {
                        @Override
                        public boolean apply(String fileName) {
                            return fileName.contains("production");
                        }


                    }).toSortedList(new Comparator<String>() {
                        @Override
                        public int compare(String o1, String o2) {
                            return o1.compareTo(o2);
                        }
                    });
            Utils.mergeFiles(files,
                    new File(folderPath, outputFileName + ".csv").getCanonicalPath());
            return new File(folderPath, outputFileName + ".csv").exists();
        }
        else {
            Process process = Runtime.getRuntime().exec("cmd /c start copy *.csv " + outputFileName + ".csv", null, new File(folderPath));
            process.waitFor();
        }

        isMerged = sync(mergeTime, 3, new SyncLoop() {
            long lastModified() throws Exception {
                File folder = new File(folderPath);
                System.out.println(folder.lastModified());
                return folder.lastModified();
            }
            boolean isModified() throws Exception {
                long lastModified = lastModified();
                Thread.sleep(3000);
                return lastModified < lastModified();

            }
            @Override
            public boolean check() throws Exception {
                return !isModified();
            }
            @Override
            public String getErrorMessage() {
                return "mergeCSVFiles - failed";
            }
        });
        return isMerged;
    }

    public static boolean sync(int maxSecond, int sleepTimeInSecond, SyncLoop syncLoop) throws Exception {
        boolean isOk = false;
        boolean doContinue = true;
        int sec = 0;
        while (doContinue){
            if(syncLoop.check()){
                isOk = true;
                doContinue = false;
            }
            else {
                try {
                    Thread.sleep(1000*sleepTimeInSecond);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                sec = sec + sleepTimeInSecond;
            }
            if(sec > maxSecond)
                doContinue = false;
        }
        if(!isOk){
            System.out.println(syncLoop.getErrorMessage());
        }
        return isOk;
    }

    public interface SyncLoop {
        boolean check() throws Exception;
        String getErrorMessage();
    }

    public static void mergeFiles(List<String> filesPath, String outPutFilePath) throws Exception {
        StringBuilder stringBuilder = new StringBuilder();
        boolean isFirst = true;
        for(String f:filesPath){
            if(!isFirst){
                stringBuilder.append("\n");
            }
            else {
                isFirst = false;
            }
            stringBuilder.append(readFile(f));
        }
        writeTextToFile(outPutFilePath, stringBuilder.toString());
    }

    public static List<String> getAllFilesInFolder(String folderPath){
        List<String> filesPath = new ArrayList<>();
        File folder = new File(folderPath);
        if(folder.exists()){
            for(File f:folder.listFiles()){
                filesPath.add(f.getAbsolutePath());
            }
        }
        return filesPath;
    }

    public static String  readFile(String fileName) throws Exception {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(fileName));
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            return sb.toString();
        }catch(Exception e){
            throw e;
        } finally {
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void writeTextToFile(String path, String text) throws Exception {
        Writer writer = null;
        try {
            File f = new File(path);
            if(!f.isFile())
                f.createNewFile();

            writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(path, true), "UTF-8"));
            writer.append(text +"\n");

        } catch (Exception e) {
            throw e;
        }
        finally {
            writer.close();
        }
    }

    public static void sortListString(List<String> list){
        Collections.sort(list, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        });
    }

    public static String getDateString(Date date, String timeZone) {
        return formatDateToPattern(date, "yyyy-MM-dd", timeZone);
    }

    public static String formatDateToPattern(Date date, String pattern, String timeZone) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        sdf.setTimeZone(TimeZone.getTimeZone(timeZone));
        return sdf.format(date);
    }

    public static void sendMail(String subject,String body,String to) throws Exception{
        String pass = "pass";
        Properties props = System.getProperties();
        props.put("mail.smtp.starttls.enable", "true"); // This line for port 587
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.user", "info@farmigo.com");
        props.put("mail.smtp.password", pass);
        props.put("mail.smtp.port", "587"); //change to 587 or 465
        props.put("mail.smtp.auth", "true"); //next two props should be commented for port 587 uncomment for 465




        Session session = Session.getInstance(props);
        MimeMessage message = new MimeMessage(session);

        System.out.println("Port: "+session.getProperty("mail.smtp.port"));

        Transport transport = null;
        // Create the email addresses involved
        try {
            InternetAddress from = new InternetAddress("info@farmigo.com");
            message.setSubject(subject);
            message.setFrom(from);
            message.addRecipients(Message.RecipientType.TO, InternetAddress.parse(to));

            // Create a multi-part to combine the parts
            Multipart multipart = new MimeMultipart("alternative");

            // Create your text message part
            BodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText("some text to send");

            // Add the text part to the multipart
            multipart.addBodyPart(messageBodyPart);

            // Create the html part
            messageBodyPart = new MimeBodyPart();
            String htmlMessage = body;
            messageBodyPart.setContent(htmlMessage, "text/html");


            // Add html part to multi part
            multipart.addBodyPart(messageBodyPart);

            // Associate multi-part with message
            message.setContent(multipart);

            // Send message
            transport = session.getTransport("smtp");
            transport.connect("smtp.gmail.com", "info@farmigo.com", pass);
            System.out.println("Transport: "+transport.toString());
            transport.sendMessage(message, message.getAllRecipients());


        } catch (AddressException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (MessagingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        finally {
            transport.close();
        }
    }

    public static void deleteAllFilesInFolder(String folderPath){
        File folder = new File(folderPath);
        System.out.println("Delete all files/folders in:" + folder.getAbsolutePath());
        String[] files = folder.list();
        for (String fileStr : files) {
            File file= new File(folder,fileStr);
            deleteDir(file);
        }
    }

    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }

        return dir.delete(); // The directory is empty now and can be deleted.
    }
}
