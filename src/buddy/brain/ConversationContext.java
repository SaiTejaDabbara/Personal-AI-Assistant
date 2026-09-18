package buddy.brain;

public class ConversationContext {

    private String lastUserMessage;

    private String lastAction;

    private String lastObject;

    private String lastLocation;

    private String lastFileName;

    private String lastFilePath;

    private String lastFolderName;

    private String pendingAction;

    private String pendingDetails;

    private boolean waitingForConfirmation;


    // ============================================================
    // LAST USER MESSAGE
    // ============================================================

    public void setLastUserMessage(String lastUserMessage) {
        this.lastUserMessage = lastUserMessage;
    }

    public String getLastUserMessage() {
        return lastUserMessage;
    }


    // ============================================================
    // LAST ACTION
    // ============================================================

    public void setLastAction(String lastAction) {
        this.lastAction = lastAction;
    }

    public String getLastAction() {
        return lastAction;
    }


    // ============================================================
    // LAST OBJECT
    // ============================================================

    public void setLastObject(String lastObject) {
        this.lastObject = lastObject;
    }

    public String getLastObject() {
        return lastObject;
    }


    // ============================================================
    // LAST LOCATION
    // ============================================================

    public void setLastLocation(String lastLocation) {
        this.lastLocation = lastLocation;
    }

    public String getLastLocation() {
        return lastLocation;
    }


    // ============================================================
    // LAST FILE NAME
    // ============================================================

    public void setLastFileName(String lastFileName) {
        this.lastFileName = lastFileName;
    }

    public String getLastFileName() {
        return lastFileName;
    }


    // ============================================================
    // LAST FILE PATH
    // ============================================================

    public void setLastFilePath(String lastFilePath) {
        this.lastFilePath = lastFilePath;
    }

    public String getLastFilePath() {
        return lastFilePath;
    }


    // ============================================================
    // LAST FOLDER NAME
    // ============================================================

    public void setLastFolderName(String lastFolderName) {
        this.lastFolderName = lastFolderName;
    }

    public String getLastFolderName() {
        return lastFolderName;
    }


    // ============================================================
    // PENDING ACTION
    // ============================================================

    public void setPendingAction(
            String action,
            String details
    ) {

        this.pendingAction = action;
        this.pendingDetails = details;
        this.waitingForConfirmation = true;
    }


    public String getPendingAction() {
        return pendingAction;
    }


    public String getPendingDetails() {
        return pendingDetails;
    }


    // ============================================================
    // CONFIRMATION STATE
    // ============================================================

    public boolean isWaitingForConfirmation() {
        return waitingForConfirmation;
    }


    // ============================================================
    // CLEAR PENDING ACTION
    // ============================================================

    public void clearPendingAction() {

        pendingAction = null;
        pendingDetails = null;
        waitingForConfirmation = false;
    }


    // ============================================================
    // CONTEXT TEXT FOR OLLAMA
    // ============================================================

    public String getContextText() {

        StringBuilder text =
                new StringBuilder();


        if (
                lastUserMessage != null
                        && !lastUserMessage.isBlank()
        ) {

            text.append(
                    "Last user message: "
            ).append(
                    lastUserMessage
            ).append("\n");
        }


        if (
                lastAction != null
                        && !lastAction.isBlank()
        ) {

            text.append(
                    "Last action: "
            ).append(
                    lastAction
            ).append("\n");
        }


        if (
                lastObject != null
                        && !lastObject.isBlank()
        ) {

            text.append(
                    "Last object: "
            ).append(
                    lastObject
            ).append("\n");
        }


        if (
                lastFileName != null
                        && !lastFileName.isBlank()
        ) {

            text.append(
                    "Last file name: "
            ).append(
                    lastFileName
            ).append("\n");
        }


        if (
                lastFilePath != null
                        && !lastFilePath.isBlank()
        ) {

            text.append(
                    "Last file path: "
            ).append(
                    lastFilePath
            ).append("\n");
        }


        if (
                lastFolderName != null
                        && !lastFolderName.isBlank()
        ) {

            text.append(
                    "Last folder name: "
            ).append(
                    lastFolderName
            ).append("\n");
        }


        if (
                lastLocation != null
                        && !lastLocation.isBlank()
        ) {

            text.append(
                    "Last location: "
            ).append(
                    lastLocation
            ).append("\n");
        }


        if (
                pendingAction != null
                        && !pendingAction.isBlank()
        ) {

            text.append(
                    "Pending action: "
            ).append(
                    pendingAction
            ).append("\n");
        }


        if (
                pendingDetails != null
                        && !pendingDetails.isBlank()
        ) {

            text.append(
                    "Pending details: "
            ).append(
                    pendingDetails
            ).append("\n");
        }


        if (
                waitingForConfirmation
        ) {

            text.append(
                    "Waiting for confirmation: yes\n"
            );
        }


        return text.toString().trim();
    }
}