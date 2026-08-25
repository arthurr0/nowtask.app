package app.nowtask.integrations;

final class GitHubText {

    private GitHubText() {
    }

    static String stripMarker(String body) {
        if (body == null) {
            return "";
        }

        String text = body.replace("<!-- nowtask -->", "").trim();
        int footer = text.lastIndexOf("\n---\n");
        return footer < 0 ? text : text.substring(0, footer).trim();
    }
}
