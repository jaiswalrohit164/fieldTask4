/*
 * Copyright 2018 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.odk.collect.android.utilities;

import android.net.Uri;

import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.RootTranslator;
import org.kxml2.kdom.Element;
import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.database.DatabaseFormsRepository;
import org.odk.collect.android.formmanagement.ServerFormDetails;
import org.odk.collect.android.forms.Form;
import org.odk.collect.android.forms.FormsRepository;
import org.odk.collect.android.listeners.FormDownloaderListener;
import org.odk.collect.android.logic.FileReferenceFactory;
import org.odk.collect.android.logic.PropertyManager;
import org.odk.collect.android.openrosa.OpenRosaXmlFetcher;
import org.odk.collect.android.openrosa.api.FormListApi;
import org.odk.collect.android.openrosa.api.MediaFile;
import org.odk.collect.android.openrosa.api.OpenRosaFormListApi;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.storage.StoragePathProvider;
import org.odk.collect.android.storage.StorageSubdirectory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

import static org.odk.collect.android.utilities.FileUtils.LAST_SAVED_FILENAME;
import static org.odk.collect.android.utilities.FileUtils.STUB_XML;
import static org.odk.collect.android.utilities.FileUtils.write;

@Deprecated
public class MultiFormDownloader {

    private static final String MD5_COLON_PREFIX = "md5:";
    private static final String TEMP_DOWNLOAD_EXTENSION = ".tempDownload";

    private final FormListApi formListApi;
    private final FormsRepository formsRepository;

    @Deprecated
    public MultiFormDownloader(OpenRosaXmlFetcher openRosaXmlFetcher) {
        this.formsRepository = new DatabaseFormsRepository();
        formListApi = new OpenRosaFormListApi(openRosaXmlFetcher);
    }

    private static final String NAMESPACE_OPENROSA_ORG_XFORMS_XFORMS_MANIFEST =
            "http://openrosa.org/xforms/xformsManifest";

    static boolean isXformsManifestNamespacedElement(Element e) {
        return e.getNamespace().equalsIgnoreCase(NAMESPACE_OPENROSA_ORG_XFORMS_XFORMS_MANIFEST);
    }

    private static class TaskCancelledException extends Exception {
        private final File file;

        TaskCancelledException(File file) {
            super("Task was cancelled during processing of " + file);
            this.file = file;
        }

        TaskCancelledException() {
            super("Task was cancelled");
            this.file = null;
        }
    }

    @Deprecated
    public HashMap<ServerFormDetails, String> downloadForms(List<ServerFormDetails> toDownload, FormDownloaderListener stateListener) {
        int total = toDownload.size();
        int count = 1;

        final HashMap<ServerFormDetails, String> result = new HashMap<>();

        for (ServerFormDetails fd : toDownload) {
            try {
                boolean success = processOneForm(total, count++, fd, stateListener);
                if (success) {
                    result.put(fd, Collect.getInstance().getString(R.string.success));
                } else {
                    result.put(fd, Collect.getInstance().getString(R.string.failure));
                }
            } catch (TaskCancelledException cd) {
                break;
            }
        }

        return result;
    }

    /**
     * Processes one form download.
     *
     * @param total the total number of forms being downloaded by this task
     * @param count the number of this form
     * @param fd    the FormDetails
     * @return an empty string for success, or a nonblank string with one or more error messages
     * @throws TaskCancelledException to signal that form downloading is to be canceled
     */
    private boolean processOneForm(int total, int count, ServerFormDetails fd, FormDownloaderListener stateListener) throws TaskCancelledException {
        if (stateListener != null) {
            stateListener.progressUpdate(fd.getFormName(), String.valueOf(count), String.valueOf(total));
        }
        boolean success = true;
        if (stateListener != null && stateListener.isTaskCanceled()) {
            throw new TaskCancelledException();
        }

        // use a temporary media path until everything is ok.
        String tempMediaPath = new File(new StoragePathProvider().getDirPath(StorageSubdirectory.CACHE),
                String.valueOf(System.currentTimeMillis())).getAbsolutePath();
        String orgTempMediaPath = new File(tempMediaPath + "_org").getAbsolutePath();      // smap
        String orgMediaPath = Utilities.getOrgMediaPath();  ;          // smap
        final String finalMediaPath;
        FileResult fileResult = null;
        try {
            String deviceId = new PropertyManager(Collect.getInstance().getApplicationContext())
                    .getSingularProperty(PropertyManager.PROPMGR_DEVICE_ID);        // smap

            // get the xml file
            // if we've downloaded a duplicate, this gives us the file
            fileResult = downloadXform(fd.getFormName(), fd.getDownloadUrl() + "&deviceID=" + deviceId, stateListener,
                    fd.getFormPath());                      // smap

            if (fd.getManifestUrl() != null) {
                finalMediaPath = FileUtils.constructMediaPath(
                        fileResult.getFile().getAbsolutePath());
                String error = downloadManifestAndMediaFiles(tempMediaPath, finalMediaPath, fd,
                        count, total, stateListener, orgTempMediaPath, orgMediaPath);                              // smap added org paths
                if (error != null && !error.isEmpty()) {
                    success = false;
                }
            } else {
                Timber.i("No Manifest for: %s", fd.getFormName());
            }
        } catch (TaskCancelledException e) {
            Timber.i(e);
            cleanUp(fileResult, e.file, tempMediaPath, orgTempMediaPath);   // smap

            // do not download additional forms.
            throw e;
        } catch (Exception e) {
            Timber.e(e);  // smap
            return false;
        }

        if (stateListener != null && stateListener.isTaskCanceled()) {
            cleanUp(fileResult, null, tempMediaPath, orgTempMediaPath);     // smap
            fileResult = null;
        }

        if (fileResult == null) {
            return false;
        }

        Map<String, String> parsedFields = null;
        if (fileResult != null && fileResult.file.exists()) {       // smap add check for exists
            try {
                final long start = System.currentTimeMillis();
                Timber.w("Parsing document %s", fileResult.file.getAbsolutePath());

                // Add a stub last-saved instance to the tmp media directory so it will be resolved
                // when parsing a form definition with last-saved reference
                File tmpLastSaved = new File(tempMediaPath, LAST_SAVED_FILENAME);
                write(tmpLastSaved, STUB_XML.getBytes(Charset.forName("UTF-8")));
                ReferenceManager.instance().reset();
                ReferenceManager.instance().addReferenceFactory(new FileReferenceFactory(tempMediaPath));
                ReferenceManager.instance().addSessionRootTranslator(new RootTranslator("jr://file-csv/", "jr://file/"));

                parsedFields = FileUtils.getMetadataFromFormDefinition(fileResult.file);
                ReferenceManager.instance().reset();
                FileUtils.deleteAndReport(tmpLastSaved);

                Timber.i("Parse finished in %.3f seconds.",
                        (System.currentTimeMillis() - start) / 1000F);
            } catch (RuntimeException e) {
                ReferenceManager.instance().reset();    // smap ensure reference manager reset after error
                return false;
            }
        }

        boolean installed = false;

        if ((stateListener == null || !stateListener.isTaskCanceled()) && parsedFields != null) {    // smap remove check on empty message and check that parsed fields is not empty
            if (!fileResult.isNew || isSubmissionOk(parsedFields)) {
                installed = installEverything(tempMediaPath, fileResult, parsedFields, fd, orgTempMediaPath, orgMediaPath);   // Added organisation paths
            } else {
                success = false;
            }
        }
        if (!installed) {
            success = false;
            cleanUp(fileResult, null, tempMediaPath, orgTempMediaPath);    // smap
        }

        return success;
    }

    private boolean isSubmissionOk(Map<String, String> parsedFields) {
        String submission = parsedFields.get(FileUtils.SUBMISSIONURI);
        return submission == null || Validator.isUrlValid(submission);
    }

    boolean installEverything(String tempMediaPath, FileResult fileResult, Map<String, String> parsedFields, 
            ServerFormDetails fd, String orgTempMediaPath, String orgMediaPath)   {   // smap add fd,  organisational paths
        UriResult uriResult = null;
        try {
            uriResult = findExistingOrCreateNewUri(fileResult.file, parsedFields,
                    STFileUtils.getSource(fd.getDownloadUrl()),
                    fd.getTasksOnly(),
                    fd.getProject());  // smap add source, tasks_only, project
            if (uriResult != null) {
                Timber.w("Form uri = %s, isNew = %b", uriResult.getUri().toString(), uriResult.isNew());

                // move the media files in the media folder
                if (tempMediaPath != null) {

                    File orgMediaDir = new File(orgMediaPath);                      // smap
                    File orgTempMediaDir = new File(orgTempMediaPath);              // smap
                    File[] orgTempFiles = orgTempMediaDir.listFiles();              // smap
                    if(orgTempFiles != null && orgTempFiles.length > 0) {           // smap Save a copy the media files in the org media directory
                        for (File mf : orgTempFiles) {
                            try {
                                FileUtils.deleteOldFile(mf.getName(), orgMediaDir);
                                if (mf.getName().endsWith(".json")) {
                                    org.apache.commons.io.FileUtils.moveFileToDirectory(mf, orgMediaDir, true);     // Move json files
                                } else {
                                    org.apache.commons.io.FileUtils.copyFileToDirectory(mf, orgMediaDir, true);     // For other files a copy is saved
                                }
                            } catch (Exception e) {
                            }
                        }
                    }

                    File formMediaPath = new File(uriResult.getMediaPath());
                    FileUtils.moveMediaFiles(orgTempMediaPath, formMediaPath);      // smap Move org files first and overwrite with form level
                    FileUtils.moveMediaFiles(tempMediaPath, formMediaPath);
                }
                return true;
            } else {
                Timber.w("Form uri = null");
            }
        } catch (IOException e) {
            Timber.e(e);

            if (uriResult.isNew() && fileResult.isNew()) {
                // this means we should delete the entire form together with the metadata
                Uri uri = uriResult.getUri();
                Timber.w("The form is new. We should delete the entire form.");
                int deletedCount = Collect.getInstance().getContentResolver().delete(uri,
                        null, null);
                Timber.w("Deleted %d rows using uri %s", deletedCount, uri.toString());
            }

            cleanUp(fileResult, null, tempMediaPath, orgTempMediaPath);     // smap add organisation
        }
        return false;
    }

    private void cleanUp(FileResult fileResult, File fileOnCancel, String tempMediaPath, String orgTempMediaPath) {     // smap add org
        if (fileResult == null) {
            Timber.w("The user cancelled (or an exception happened) the download of a form at the "
                    + "very beginning.");
        } else {
            if(fileResult.file.exists()) {  // smap
                String md5Hash = FileUtils.getMd5Hash(fileResult.file);
                if (md5Hash != null) {
                    formsRepository.deleteFormsByMd5Hash(md5Hash);
                }
            }
        }

        FileUtils.deleteAndReport(fileOnCancel);

        if (tempMediaPath != null) {
            FileUtils.purgeMediaPath(tempMediaPath);
        }
        if (orgTempMediaPath != null) {     // smap
            FileUtils.purgeMediaPath(orgTempMediaPath);
        }
    }

    /**
     * Creates a new form in the database, if none exists with the same absolute path. Returns
     * information with the URI, media path, and whether the form is new.
     *
     * @param formFile the form definition file
     * @param formInfo certain fields extracted from the parsed XML form, such as title and form ID
     * @return a {@link UriResult} object
     */
    private UriResult findExistingOrCreateNewUri(File formFile, Map<String, String> formInfo,
                                                 String source, boolean tasks_only, String project) {   // smap add source, tasks_only, project
        final Uri uri;
        final String formFilePath = formFile.getAbsolutePath();
        String mediaPath = FileUtils.constructMediaPath(formFilePath);

        FileUtils.checkMediaPath(new File(mediaPath));


        Form form = formsRepository.getByPath(formFile.getAbsolutePath());

        if (form == null) {
            uri = saveNewForm(formInfo, formFile, mediaPath, tasks_only, source, project);       // smap add tasks_only and source
            return new UriResult(uri, mediaPath, true);
        } else {
            uri = Uri.withAppendedPath(FormsColumns.CONTENT_URI, form.getId().toString());
            mediaPath = new StoragePathProvider().getAbsoluteFormFilePath(form.getFormMediaPath());

            if (form.isDeleted()) {
                formsRepository.restore(form.getId());
            }

            return new UriResult(uri, mediaPath, false);
        }
    }

    private Uri saveNewForm(Map<String, String> formInfo, File formFile, String mediaPath,
                            boolean tasks_only, String source, String project) {    // smap add tasks_only, source project
        Form form = new Form.Builder()
                .formFilePath(new StoragePathProvider().getFormDbPath(formFile.getAbsolutePath()))
                .formMediaPath(new StoragePathProvider().getFormDbPath(mediaPath))
                .displayName(formInfo.get(FileUtils.TITLE))
                .jrVersion(formInfo.get(FileUtils.VERSION))
                .jrFormId(formInfo.get(FileUtils.FORMID))
                .project(project)      // smap
                .tasksOnly(tasks_only ? "yes" : "no")   // smap
                .source(source)       // smap
                .submissionUri(formInfo.get(FileUtils.SUBMISSIONURI))
                .base64RSAPublicKey(formInfo.get(FileUtils.BASE64_RSA_PUBLIC_KEY))
                .autoDelete(formInfo.get(FileUtils.AUTO_DELETE))
                .autoSend(formInfo.get(FileUtils.AUTO_SEND))
                //.geometryXpath(formInfo.get(FileUtils.GEOMETRY_XPATH))     // smap
                .build();

        return formsRepository.save(form);
    }

    /**
     * Takes the formName and the URL and attempts to download the specified file. Returns a file
     * object representing the downloaded file.
     */
    FileResult downloadXform(String formName, String url, FormDownloaderListener stateListener, String formPath) throws Exception {
        // clean up friendly form name...
        String rootName = FormNameUtils.formatFilenameFromFormName(formName);

        File f;
        boolean isNew = false;      // smap
        StoragePathProvider storagePathProvider = new StoragePathProvider(); // smap
        // proposed name of xml file...
        //StoragePathProvider storagePathProvider = new StoragePathProvider();  // smap commented out
        String path = storagePathProvider.getDirPath(StorageSubdirectory.FORMS) + File.separator + rootName + ".xml";
        int i = 2;
        f = new File(path);

        InputStream file = formListApi.fetchForm(url, true);    // smap add credentials flag
        writeFile(f, stateListener, file);    // smap credentials flag

        // we've downloaded the file, and we may have renamed it
        // make sure it's not the same as a file we already have
        Form form = formsRepository.getByMd5Hash(FileUtils.getMd5Hash(f));
        if (form != null) {
            isNew = false;

            // delete the file we just downloaded, because it's a duplicate
            Timber.w("A duplicate file has been found, we need to remove the downloaded file "
                    + "and return the other one.");
            FileUtils.deleteAndReport(f);

            // set the file returned to the file we already had
            String existingPath = storagePathProvider.getAbsoluteFormFilePath(form.getFormFilePath());
            f = new File(existingPath);
            Timber.w("Will use %s", existingPath);
        } else {
            if(formPath == null) {
                f = new File(storagePathProvider.getDirPath(StorageSubdirectory.FORMS) + File.separator + rootName + ".xml");   // smap
            } else {
                f = new File(formPath);     // smap
            }
        }
        return new FileResult(f, isNew);    // smap

    }

    /**
     * Common routine to take a downloaded document save the contents in the file
     * 'file'. Shared by media file download and form file download.
     * <p>
     * SurveyCTO: The file is saved into a temp folder and is moved to the final place if everything
     * is okay, so that garbage is not left over on cancel.
     *
     */
    public void writeFile(File file, FormDownloaderListener stateListener, InputStream inputStream)   // smap made public
            throws IOException, TaskCancelledException, URISyntaxException, Exception {

        File tempFile = File.createTempFile(file.getName(), TEMP_DOWNLOAD_EXTENSION,
                new File(new StoragePathProvider().getDirPath(StorageSubdirectory.CACHE)));

        // WiFi network connections can be renegotiated during a large form download sequence.
        // This will cause intermittent download failures.  Silently retry once after each
        // failure.  Only if there are two consecutive failures do we abort.
        boolean success = false;
        int attemptCount = 0;
        final int MAX_ATTEMPT_COUNT = 2;
        while (!success && ++attemptCount <= MAX_ATTEMPT_COUNT) {
            if (stateListener != null && stateListener.isTaskCanceled()) {
                throw new TaskCancelledException(tempFile);
            }

            // write connection to file
            InputStream is = null;
            OutputStream os = null;

            try {
                is = inputStream;
                os = new FileOutputStream(tempFile);

                byte[] buf = new byte[4096];
                int len;
                while ((len = is.read(buf)) > 0 && (stateListener == null || !stateListener.isTaskCanceled())) {
                    os.write(buf, 0, len);
                }
                os.flush();
                success = true;

            } catch (Exception e) {
                Timber.e(e.toString());
                // silently retry unless this is the last attempt,
                // in which case we rethrow the exception.

                FileUtils.deleteAndReport(tempFile);

                if (attemptCount == MAX_ATTEMPT_COUNT) {
                    throw e;
                }
            } finally {
                if (os != null) {
                    try {
                        os.close();
                    } catch (Exception e) {
                        Timber.e(e);
                    }
                }
                if (is != null) {
                    try {
                        // ensure stream is consumed...
                        final long count = 1024L;
                        while (is.skip(count) == count) {
                            // skipping to the end of the http entity
                        }
                    } catch (Exception e) {
                        // no-op
                    }
                    try {
                        is.close();
                    } catch (Exception e) {
                        Timber.e(e);
                    }
                }
            }

            if (stateListener != null && stateListener.isTaskCanceled()) {
                FileUtils.deleteAndReport(tempFile);
                throw new TaskCancelledException(tempFile);
            }
        }

        Timber.d("Completed downloading of %s. It will be moved to the proper path...",
                tempFile.getAbsolutePath());

        FileUtils.deleteAndReport(file);

        String errorMessage = FileUtils.copyFile(tempFile, file);

        if (file.exists()) {
            Timber.w("Copied %s over %s", tempFile.getAbsolutePath(), file.getAbsolutePath());
            FileUtils.deleteAndReport(tempFile);
        } else {
            String msg = Collect.getInstance().getString(R.string.fs_file_copy_error,
                    tempFile.getAbsolutePath(), file.getAbsolutePath(), errorMessage);
            Timber.w(msg);
            throw new RuntimeException(msg);
        }
    }

    private static class UriResult {

        private final Uri uri;
        private final String mediaPath;
        private final boolean isNew;

        private UriResult(Uri uri, String mediaPath, boolean isNew) {
            this.uri = uri;
            this.mediaPath = mediaPath;
            this.isNew = isNew;
        }

        private Uri getUri() {
            return uri;
        }

        private String getMediaPath() {
            return mediaPath;
        }

        private boolean isNew() {
            return isNew;
        }
    }

    public static class FileResult {   // smap make public

        private final File file;
        private final boolean isNew;

        public FileResult(File file, boolean isNew) {   // smap make public
            this.file = file;
            this.isNew = isNew;
        }

        public File getFile() {    // smap make public
            return file;
        }

        public boolean isNew() {    // smap make public
            return isNew;
        }
    }

    String downloadManifestAndMediaFiles(String tempMediaPath, String finalMediaPath,
                                         ServerFormDetails fd, int count,
                                         int total, FormDownloaderListener stateListener,
                                                 String orgTempMediaPath,   // smap
                                                 String orgMediaPath) throws Exception {        // smap
        if (fd.getManifestUrl() == null) {
            return null;
        }

        StringBuffer downloadMsg = new StringBuffer("");        // smap
        if (stateListener != null) {
            stateListener.progressUpdate(Collect.getInstance().getString(R.string.fetching_manifest, fd.getFormName()),
                    String.valueOf(count), String.valueOf(total));
        }

        List<MediaFile> files = formListApi.fetchManifest(fd.getManifestUrl()).getMediaFiles();

        // OK we now have the full set of files to download...
        Timber.i("Downloading %d media files.", files.size());
        int mediaCount = 0;
        if (!files.isEmpty()) {
            File tempMediaDir = new File(tempMediaPath);
            File orgTempMediaDir = new File(orgTempMediaPath);  // smap organisational media
            File finalMediaDir = new File(finalMediaPath);
            File orgMediaDir = new File (orgMediaPath);         // smap organisational media

            FileUtils.checkMediaPath(tempMediaDir);
            FileUtils.checkMediaPath(orgTempMediaDir);          // smap
            FileUtils.checkMediaPath(finalMediaDir);
            FileUtils.checkMediaPath(orgMediaDir);              // smap

            for (MediaFile toDownload : files) {
                ++mediaCount;
                if (stateListener != null) {
                    stateListener.progressUpdate(
                            Collect.getInstance().getString(R.string.form_download_progress,
                                    fd.getFormName(),
                                    String.valueOf(mediaCount), String.valueOf(files.size())),
                            String.valueOf(count), String.valueOf(total));
                }

                //try {
                // start smap organisational media
                File finalMediaFile = null;
                File finalImportedMediaFile = null;
                File tempMediaFile = null;
                if(toDownload.getDownloadUrl().endsWith("organisation")) {
                    finalMediaFile = new File(orgMediaDir, toDownload.getFilename());
                    tempMediaFile = new File(orgTempMediaDir, toDownload.getFilename());
                } else {
                    finalMediaFile = new File(finalMediaDir, toDownload.getFilename());
                    tempMediaFile = new File(tempMediaDir, toDownload.getFilename());
                    if(finalMediaFile.getName().endsWith(".csv")) {
                        finalImportedMediaFile = new File(finalMediaDir, toDownload.getFilename() + ".imported");
                    }
                }

                // end smap
                if (!finalMediaFile.exists()) {
                    InputStream mediaFile = formListApi.fetchMediaFile(toDownload.getDownloadUrl(), true);  // smap add credentials file
                    writeFile(tempMediaFile, stateListener, mediaFile);
                } else {
                    String currentFileHash = FileUtils.getMd5Hash(finalMediaFile);
                    if(currentFileHash == null && finalImportedMediaFile != null && finalImportedMediaFile.exists()) { // smap get hash from imported if necessary
                        currentFileHash = FileUtils.getMd5Hash(finalImportedMediaFile);
                    }
                    String downloadFileHash = getMd5Hash(toDownload.getHash());

                    if (currentFileHash != null && downloadFileHash != null && !currentFileHash.contentEquals(downloadFileHash)) {
                        // if the hashes match, it's the same file
                        // otherwise delete our current one and replace it with the new one
                        FileUtils.deleteAndReport(finalMediaFile);
                        downloadMsg.append(Collect.getInstance().getString(R.string.smap_get_media, tempMediaFile.getName())).append("\n");     // smap
                        InputStream mediaFile = formListApi.fetchMediaFile(toDownload.getDownloadUrl(), true);  // smap credentials flag
                        writeFile(tempMediaFile, stateListener, mediaFile);
                    } else {
                        // exists, and the hash is the same
                        // no need to download it again
                        Timber.i("Skipping media file fetch -- file hashes identical: %s",
                                finalMediaFile.getAbsolutePath());

                        if(toDownload.getDownloadUrl().endsWith("organisation")) {  // smap Lets get this file and copy it to the form
                            try {
                                org.apache.commons.io.FileUtils.copyFileToDirectory(finalMediaFile, finalMediaDir, true);
                            } catch(Exception e) {
                                // Ignore an error if the file already exists
                            }
                        }
                    }
                }
                //  } catch (Exception e) {
                //  return e.getLocalizedMessage();
                //}
            }
        }
        if(downloadMsg.length() > 0) {      // smap
            return downloadMsg.toString();
        } else {
            return null;
        }
    }

    public static String getMd5Hash(String hash) {
        return hash == null || hash.isEmpty() ? null : hash.substring(MD5_COLON_PREFIX.length());
    }
}
