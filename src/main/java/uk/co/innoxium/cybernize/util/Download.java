package uk.co.innoxium.cybernize.util;


import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Observable;
import java.util.Observer;

/**
 * Created by Zach Piddock on 17/11/2015.
 */
public class Download extends Observable implements Runnable {

    // Max size of download buffer.
    private static final int MAX_BUFFER_SIZE = 4096;

    // These are the status names.
    public static final String STATUSES[] = { "Downloading", "Paused", "Complete", "Cancelled", "Error" };

    // These are the status codes.
    public static final int DOWNLOADING = 0;
    public static final int PAUSED = 1;
    public static final int COMPLETE = 2;
    public static final int CANCELLED = 3;
    public static final int ERROR = 4;

    private URL url; // download URL
    private int size; // size of download in bytes
    private int downloaded; // number of bytes downloaded
    private int status; // current status of download
    private static Observer defaultObserver = new DefaultDownloadObserver();
    private File folder;

    // Constructor for Download.
    public Download(URL url, Observer observer, File folder) {

        this.url = url;
        size = -1;
        downloaded = 0;
        status = DOWNLOADING;
        this.folder = folder;
        addObserver(observer);
    }

    public Download(URL url, File folder) {

        this(url, defaultObserver, folder);
    }

    public Download(URL url) {

        this(url, new File(".", "test"));
    }

    // Get this download's URL.
    public String getUrl() {

        return url.toString();
    }

    // Get this download's size.
    public int getSize() {

        return size;
    }
    
    public int getDownloaded() {
    	
    	return downloaded;
    }

    // Get this download's progress.
    public float getProgress() {

        return ((float)downloaded / size) * 100;
    }

    // Get this download's status.
    public int getStatus() {

        return status;
    }

    // Pause this download.
    public void pause() {

        status = PAUSED;
        stateChanged();
    }

    // Resume this download.
    public void resume() {

        status = DOWNLOADING;
        stateChanged();
        download();
    }

    // Cancel this download.
    public void cancel() {

        status = CANCELLED;
        stateChanged();
    }

    // Mark this download as having an error.
    private void error() {

        status = ERROR;
        stateChanged();
    }

    // Start or resume downloading.
    public void download() {

        Thread thread = new Thread(this);
        thread.start();
    }

    // Download file.
    public void run() {

        File file;
        InputStream stream = null;
        FileOutputStream outputStream = null;

        try {
            // Open connection to URL.

            HttpURLConnection connection;
            switch(url.getProtocol()) {

                case "http" -> connection = (HttpURLConnection)url.openConnection();
                case "https" -> connection = (HttpsURLConnection)url.openConnection();
                default -> throw new IOException(String.format("Invalid Protocol \"%s\", must either be http or https", url.getProtocol()));
            }

            // Specify what portion of file to download.
            connection.setRequestProperty("Range", "bytes=" + downloaded + "-");

            // Connect to server.
            connection.connect();

            // Make sure response code is in the 200 range.
            if(connection.getResponseCode() / 100 != 2) {

                error();
                return;
            }

            // Check for valid content length.
            int contentLength = connection.getContentLength();
            if(contentLength < 1) {

                stateChanged();
                // If this method gets hit, there will be no reporting of status
                if(tryStreamDataInstead(connection)) {

                    return;
                }
                error();
                return;
            }

      /* Set the size for this download if it
         hasn't been already set. */
            if(size == -1) {

                size = contentLength;
                stateChanged();
            }

            file = new File(folder, Utils.getFileName(url));

            if(!file.getParentFile().exists()) file.getParentFile().mkdirs();
            outputStream = new FileOutputStream(file);

            stream = connection.getInputStream();
            while(status == DOWNLOADING) {
        /* Size buffer according to how much of the
           file is left to download. */
                //                if(downloaded >= size)
                //                    break;

                byte buffer[];
                if(size - downloaded > MAX_BUFFER_SIZE) {
                    buffer = new byte[MAX_BUFFER_SIZE];
                } else {
                    buffer = new byte[size - downloaded];
                }

                // Read from server into buffer.
                int read = stream.read(buffer);
                if(read == -1) break;

                // Write buffer to file.
                outputStream.write(buffer, 0, read);
                downloaded += read;
                stateChanged();
            }

      /* Change status to complete if this point was
         reached because downloading has finished. */
            if(status == DOWNLOADING) {
                status = COMPLETE;
                stateChanged();
            }
        } catch(Exception e) {

            error();
            e.printStackTrace();
        } finally {
            // Close file.
            if(outputStream != null) {
                try {
                    outputStream.close();
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }

            // Close connection to server.
            if(stream != null) {
                try {
                    stream.close();
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private boolean tryStreamDataInstead(HttpURLConnection connection) {

        File file;
        InputStream stream = null;
        FileOutputStream outputStream = null;

        try {

            file = new File(folder, Utils.getFileName(url));

            if(!file.getParentFile().exists()) file.getParentFile().mkdirs();
            outputStream = new FileOutputStream(file);

            stream = connection.getInputStream();

            while(status == DOWNLOADING) {

                stream.transferTo(outputStream);
                System.out.println("Downloaded");
                status = COMPLETE;
                stateChanged();
            }
            return true;
        } catch(IOException e) {

            e.printStackTrace();
        } finally {

            // Close file.
            if(outputStream != null) {

                try {

                    outputStream.close();
                } catch(Exception e) {

                    e.printStackTrace();
                }
            }

            // Close connection to server.
            if(stream != null) {

                try {

                    stream.close();
                } catch(Exception e) {

                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    // Notify observers that this download's status has changed.
    private void stateChanged() {

        setChanged();
        notifyObservers();
    }

    static class DefaultDownloadObserver implements Observer {

        @Override
        public void update(Observable o, Object arg) {

            Download download = (Download)o;

            switch(download.status) {

                case DOWNLOADING -> System.out.println("Progress = " + download.getProgress() + ", " +
                        MathUtils.humanReadableByteCount(download.downloaded, false) + " / " +
                        MathUtils.humanReadableByteCount(download.size, false));
                case COMPLETE -> System.out.print("File Download Completed");
                case ERROR -> System.out.println("An Error Has Occurred");
            }
        }
    }
}
