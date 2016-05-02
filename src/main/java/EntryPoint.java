import javax.rmi.CORBA.Util;
import java.io.File;

/**
 * Created by Dudi on 5/1/2016.
 */
public class EntryPoint {

    public static void main(String[] args) throws Exception {
        String date = "2016-04-10";
        String outputFileName = "All_" + date;
        String localDownloadPath = new File(System.getProperty("user.dir"), "ET_Download").getCanonicalPath();;
        String localFolderPath = new File(localDownloadPath, date).getCanonicalPath();

        System.out.println("Download ET files");
        boolean res = Utils.downloadFilesFromGsFolder(ET_Const.GS_ET_FOLDER
                + date, localDownloadPath,
                localFolderPath,
                Utils.GS_DOWNLOAD_ET_TIME);

        System.out.println("mergeCSVFiles ET files");
        if(res){
            res = Utils.mergeCSVFiles(localFolderPath, outputFileName, Utils.ET_MERGE_CSV_FILES_TIME);
            if(res){
                System.out.println("Upload ET files");
                ExactTarget exactTarget = new ExactTarget(ET_Const.SERVER, ET_Const.USER, ET_Const.PASS, ET_Const.PORT);
                res = Utils.uploadFileToFtp(exactTarget, new File(localFolderPath, outputFileName + ".csv").getCanonicalPath(),
                        ET_Const.ET_SUBSCRIBERS_FILE_NAME.replace("CURRENT_DATE", date));
            }
        }
        if(!res){
            throw new Exception("failed");
        }
    }

}
