package app.nowtask.integrations;

record GitHubIssue(int number, String nodeId, String url, String state, String title, String body) {
}
