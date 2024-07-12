package de.thwildau.f4f.studycompanion.datamodel;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.LinkedList;

import de.thwildau.f4f.studycompanion.Utils;
import de.thwildau.f4f.studycompanion.backend.BackendIO;

public class StaticResourcesProvider {
    private static final String LOG_TAG = "StaticResourceProvider";
    private static final String STATIC_RESOURCES_SUBFOLDER = "StaticResources";

    private final LinkedList<StaticResources.StaticResource> queue;
    private final String dataPath;

    private boolean processing;


    private class ResourceDownloadRunnable implements Runnable {

        private final StaticResources.StaticResource staticResource;

        public ResourceDownloadRunnable(StaticResources.StaticResource staticResource) {
            this.staticResource = staticResource;
        }


        @Override
        public void run() {
            if ( !Utils.isAnyConnection()  || staticResource.isUpdateOnWifiOnly() && !Utils.isUnmeteredConnection() ) {
                return;
            }

            String downloadUrl = "(unspecified)";
            try {
                downloadUrl = BackendIO.getServerUrl() + staticResource.getPath();
                URL url = new URL(downloadUrl);

                File targetFile = new File(dataPath, staticResource.getPath());

                // create parent directory if not exists
                File targetParent = targetFile.getParentFile();
                if(!targetParent.exists()) {
                    targetParent.mkdirs();
                }

                InputStream in = new BufferedInputStream(url.openStream(),8192);
                OutputStream out = new FileOutputStream(targetFile);

                byte[] data = new byte[1024];
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
                BackendIO.serverLog(Log.ERROR, LOG_TAG,"Error downloading static resource file: " + downloadUrl);
                e.printStackTrace();
            }

            // finish for this image. Continue with next (if available).
            processNextDownload();
        }
    }

    private StaticResourcesProvider(Context context) {
        queue = new LinkedList<>();
        File dataDir = new File(context.getCacheDir().toString(), STATIC_RESOURCES_SUBFOLDER);
        dataPath = dataDir.getPath();
        if(!dataDir.exists()) {
            dataDir.mkdirs();
        }
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

        StaticResources.StaticResource nextResource = queue.poll();

        new Thread(new ResourceDownloadRunnable(nextResource)).start();
    }

    private void updateResourceCache() {
        Field[] staticResourcesFields = StaticResources.class.getFields();

        // extract all static resources defined in StaticResources class using Reflections and add them to queue.
        for (Field field : staticResourcesFields ) {
            if(Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()) && field.getType().equals(StaticResources.StaticResource.class)) {
                try {
                    StaticResources.StaticResource staticResource = (StaticResources.StaticResource) field.get(null);
                    queue.add(staticResource);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        startProcessing();
    }

    public static void updateResourceCache(Context context) {
        new StaticResourcesProvider(context).updateResourceCache();
    }

    public static File getStaticResourceFile(Context context, StaticResources.StaticResource staticResource) {
        File dataDir = new File(context.getCacheDir().toString(), STATIC_RESOURCES_SUBFOLDER);
        File resourceFile = new File(dataDir, staticResource.getPath());

        return resourceFile.exists() ? resourceFile : null;
    }

}
