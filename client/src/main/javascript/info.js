const GRAKN_MYTHOS_VERSION = require('../../../package.json').version;
window.GRAKN_MYTHOS_VERSION = GRAKN_MYTHOS_VERSION;
document.getElementById("grakn_mythos_version").textContent = "v" + GRAKN_MYTHOS_VERSION;
