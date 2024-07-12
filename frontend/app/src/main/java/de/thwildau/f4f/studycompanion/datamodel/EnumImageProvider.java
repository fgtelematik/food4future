package de.thwildau.f4f.studycompanion.datamodel;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.webkit.URLUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.datamodel.realmobjects.Enumeration;
import io.realm.Realm;
import io.realm.RealmQuery;

public class EnumImageProvider {
    private static final String LOG_TAG="EnumImageDownloader";
    private static final String ENUM_IMAGES_SUBFOLDER = "EnumImages"; // This subfolder will be created in the app's cache directory

    private class UpdateableImage {
        private String enumId;
        private String elementId;
        private String imageUrl;

        public UpdateableImage(String enumId, String elementId, String imageUrl) {
            this.enumId = enumId;
            this.elementId = elementId;
            this.imageUrl = imageUrl;
        }
    }

    private class ImageDownloadRunnable implements Runnable {

        private UpdateableImage updateableImage;

        public ImageDownloadRunnable(UpdateableImage updateableImage) {
            this.updateableImage = updateableImage;
        }


        @Override
        public void run() {
            String downloadUrl = "(unspecified)";
            try {
                downloadUrl = BackendIO.getServerUrl() + updateableImage.imageUrl;
                URL url = new URL(downloadUrl);
                String extension = ".jpg";
                if(isPngFile(downloadUrl))
                    extension =".png";

                // delete previous image file if existing
                deleteImage(updateableImage.enumId, updateableImage.elementId);

                String targetPath = dataPath + "/" + Utils.filterSpecialCharacters(updateableImage.enumId) + "_" +  Utils.filterSpecialCharacters(updateableImage.elementId) + extension;

                InputStream in = new BufferedInputStream(url.openStream(),8192);
                OutputStream out = new FileOutputStream(targetPath);

                byte data[] = new byte[1024];
                int count;
                long total = 0;

                // Reading data from remote source and write to local file
                while ((count = in.read(data)) != -1) {
                    total += count;
                    out.write(data, 0, count);
                }

                out.flush();

                in.close();
                out.close();

            } catch (Exception e) {
                BackendIO.serverLog(Log.ERROR, LOG_TAG,"Error on downloading enum image. Enum ID: " + updateableImage.enumId + ", Element ID: " + updateableImage.elementId + ", Download URL: " + downloadUrl + "(" + e + ")");
                e.printStackTrace();
            }

            // finish for this image. Continue with next (if available).
            processNextDownload();

        }
    }

    private final LinkedList<UpdateableImage> queue;
    private final String dataPath;
    private boolean processing = false;


    private static boolean isPngFile(String url) {
        String extension = url.substring(url.lastIndexOf(".") + 1);
        return extension.equalsIgnoreCase("png");
    }

    private void startProcessing() {
        if(processing) {
            return;
        }
        processNextDownload();
    }

    private void processNextDownload() {
        if(queue.size() == 0) {
            processing = false;
            return;
        }

        processing = true;
        UpdateableImage updateableImage =  queue.poll();
        new Thread(new ImageDownloadRunnable(updateableImage)).start();
    }

    public EnumImageProvider(Context context) {
        queue = new LinkedList<>();
        dataPath = context.getCacheDir().toString() + "/" + ENUM_IMAGES_SUBFOLDER;
        File dataDir = new File(dataPath);
        if(!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    private boolean isMarkedForDownload(String enumId, String elementId) {
        for(UpdateableImage markedImage : queue) {
            if(
                    markedImage.elementId.equals(elementId) && markedImage.enumId.equals(enumId)
            ) {
                return true;
            }
        }
        return false;
    }

    private void deleteImage(String enumId, String elementId) {
        File imageFile = getImageFile(enumId, elementId);
        if(imageFile != null) {
            imageFile.delete();
        }
    }

    private File getImageFile(String enumId, String elementId) {
        String fileStr = dataPath + "/" + Utils.filterSpecialCharacters(enumId) + "_" + Utils.filterSpecialCharacters(elementId);
        File fPNG = new File( fileStr  + ".jpg");
        File fJPG = new File(fileStr + ".png");

        if(fPNG.exists()) {
            return fPNG;
        }
        if(fJPG.exists()) {
            return fJPG;
        }

        return null;
    }

    private void deleteImages(String enumId) {
        File imagesDir = new File(dataPath);
        File[] imageFiles = imagesDir.listFiles();

        if(imageFiles == null) {
            return;
        }

        for(File imageFile : imageFiles) {
            String imgFilename = imageFile.getName();
            if(imgFilename.split("_")[0].equals(enumId)) {
                imageFile.delete();
            }
        }
    }


    private Map<String, String> getOldImageUrls(Realm r, String enumId) throws JSONException {
        HashMap<String, String> res = new HashMap<>();

        // Obtain last stored image urls
        RealmQuery<Enumeration> schemaQuery = r.where(Enumeration.class).equalTo("id", enumId);
        Enumeration enumeration = schemaQuery.findFirst();

        if(enumeration == null) {
            // Local enum didn't exist before
            return res;
        }

        JSONObject enumObj = new JSONObject(enumeration.jsonSchema);

        if(!enumObj.has("element_image_urls")) {
            // Local enum did not contain any images before
            return res;
        }

        JSONArray elementIds = enumObj.getJSONArray("element_ids");
        JSONArray elementImageUrls = enumObj.getJSONArray("element_image_urls");

        int length = elementIds.length();

        for(int i = 0; i < length; i++) {
            if(elementImageUrls.isNull(i)) {
                res.put(elementIds.getString(i), null);
            } else {
                res.put(elementIds.getString(i), elementImageUrls.getString(i));
            }
        }

        return res;
    }


    public void processNewEnumArray(JSONArray enums) {
        Realm r = Realm.getDefaultInstance();

        for(int i = 0; i < enums.length(); i++) {
            try {
                JSONObject enumObj = enums.getJSONObject(i);
                String enumId = enumObj.getString("id");

                if(!enumObj.has("element_image_urls")) {
                    // Delete all regarding images if enum does not contain any image urls
                    deleteImages(enumId);
                    continue;
                }

                Map<String, String> oldImageUrls = getOldImageUrls(r, enumId);

                JSONArray elementIds = enumObj.getJSONArray("element_ids");
                JSONArray elementImageUrls = enumObj.getJSONArray("element_image_urls");
                int length = elementIds.length();
                for(int j = 0; j < length; j++ ) {
                    String elementId = elementIds.getString(j);

                    String newImageUrl;
                    if(elementImageUrls.isNull(j)) {
                        newImageUrl = null;
                    } else {
                        newImageUrl = elementImageUrls.getString(j);
                        if(!URLUtil.isValidUrl(BackendIO.getServerUrl() + newImageUrl)) {
                            newImageUrl = null;
                        }
                    }

                    String oldImageUrl = oldImageUrls.get(elementId);

                    if(newImageUrl == null) {
                        deleteImage(enumId, elementId);
                    } else {
                        if(oldImageUrl == null || !oldImageUrl.equals(newImageUrl) || (getImageFile(enumId, elementId) == null && !isMarkedForDownload(enumId, elementId)) ) {
                            // Mark image for download, if URl was added or updated on remote or if local image file was deleted (e.g. when App cache was cleaned) and ist not already marked for being downloaded
                            queue.add(new UpdateableImage(enumId, elementId, newImageUrl));
                        }
                    }

                    oldImageUrls.remove(elementId);
                }

                for(String elemId : oldImageUrls.keySet()) {
                    // these remaining enum elements have been existing before but do not longer exist remotely
                    // so we delete the local image files (if existing)
                    deleteImage(enumId, elemId);
                }

            } catch(JSONException e) {
                Log.e(LOG_TAG, "Error on processing enum JSON Object.");
                e.printStackTrace();
            }
        }

        r.close();

        startProcessing();
    }

    public static Drawable getDrawableForEnumEntry(Context context, String enumId, String entryId) {
        EnumImageProvider imageProvider = new EnumImageProvider(context);
        File imageFile = imageProvider.getImageFile(enumId, entryId);
        if(imageFile == null) {
            return null;
        }

        return Drawable.createFromPath(imageFile.getPath());
    }
}
