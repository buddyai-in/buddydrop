# shellcheck shell=bash
#
# Shared helpers for scripts/*.sh. Source it; don't execute it.

# Prompt for a required value when it is missing. An interactive terminal gets a prompt (looped
# until non-empty); without a terminal (e.g. CI) the script fails with guidance instead.
#   prompt_var VARNAME "description" [secret]
# Callers may set PROMPT_CONFIG_HINT to say where the value can be configured.
prompt_var() {
  local name="$1" desc="$2" secret="${3:-}" val="${!1:-}"
  while [[ -z "${val}" ]]; do
    if [[ ! -t 0 ]]; then
      echo "ERROR: required ${name} (${desc}) is not set, and there is no terminal to prompt on." >&2
      echo "       Set it in ${PROMPT_CONFIG_HINT:-the environment}, then re-run." >&2
      exit 1
    fi
    if [[ -n "${secret}" ]]; then
      read -r -s -p "  ${desc} [${name}]: " val; echo
    else
      read -r -p "  ${desc} [${name}]: " val
    fi
  done
  printf -v "${name}" '%s' "${val}"
  export "${name}"
}

# Ask a yes/no question on the terminal; succeeds only on y/yes. Without a terminal (e.g. CI) it
# answers "no" without asking, so optional steps are skipped rather than blocking.
#   confirm "question"
confirm() {
  [[ -t 0 ]] || return 1
  local answer
  read -r -p "  $1 [y/N]: " answer
  [[ "${answer}" =~ ^[Yy]([Ee][Ss])?$ ]]
}
