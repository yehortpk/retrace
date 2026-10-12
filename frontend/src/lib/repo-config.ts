/** The directory the agent plugin reads in a repository's root. It must never be committed. */
export const PROJECT_MEMORY_DIRECTORY = '.project-memory/';

/** Creates the directory and keeps it out of git, in one line to paste in the repository's root. */
export const BIND_REPO_COMMAND =
  `mkdir -p ${PROJECT_MEMORY_DIRECTORY} && echo '${PROJECT_MEMORY_DIRECTORY}' >> .gitignore`;

/**
 * The contents of `.project-memory/config.json`. There is no project id in it: the key belongs to one
 * project, and the plugin asks the server which one.
 */
export function buildRepoConfig(apiKey: string, url: string = window.location.origin) {
  return JSON.stringify({ url, apiKey }, null, 2);
}
