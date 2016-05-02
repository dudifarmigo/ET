import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

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
            Process process = Runtime.getRuntime().exec("cat *.csv >" + outputFileName + ".csv", null, new File(folderPath));
            process.waitFor();
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
}
