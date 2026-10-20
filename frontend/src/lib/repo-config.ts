/** The file the agent plugin reads from a repository's root. It must never be committed. */
export const KEY_FILE_NAME = '.retracekey';

/**
 * The command to paste into the agent, which binds the repository to this project. The plugin
 * verifies the key before storing it, writes it to the key file, keeps it out of git, and names the
 * project it bound. There is no project id in it: the key belongs to one project, and the plugin
 * asks the server which one.
 */
export function buildBindCommand(apiKey: string) {
  return `/init-retrace ${apiKey}`;
}

/** Binding by hand, for an agent without the plugin installed. */
export function buildManualBindCommand(apiKey: string) {
  return `echo '${apiKey}' > ${KEY_FILE_NAME} && chmod 600 ${KEY_FILE_NAME}`
    + ` && echo '${KEY_FILE_NAME}' >> .gitignore`;
}
