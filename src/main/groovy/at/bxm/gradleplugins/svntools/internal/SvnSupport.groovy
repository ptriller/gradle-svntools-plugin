package at.bxm.gradleplugins.svntools.internal

import at.bxm.gradleplugins.svntools.api.SvnData
import groovy.util.logging.Log
import org.gradle.api.InvalidUserDataException
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions
import org.tmatesoft.svn.core.wc.SVNClientManager
import org.tmatesoft.svn.core.wc.SVNRevision

@Log
class SvnSupport {
  static SVNClientManager createSvnClientManager(String username, String password, SvnProxy proxy) {
    def authManager = new BasicAuthenticationManager(username, password)
    if (proxy?.host) {
      log.info "Using proxy $proxy"
      authManager.setProxy(proxy.host, proxy.port, proxy.username, proxy.password)
    }
    return SVNClientManager.newInstance(
            // create a local SVN config dir to make sure we don't reuse existing credentials:
            new DefaultSVNOptions(new File(".subversion"), true), authManager)
  }

  static SvnData createSvnData(File srcPath, String username, String password, SvnProxy proxy, boolean ignoreErrors) {
    def result = new SvnData()
    try {
      def info = createSvnClientManager(username, password, proxy).WCClient.doInfo srcPath, SVNRevision.WORKING
      result.revisionNumber = info.committedRevision.number
      result.url = info.URL
      result.repositoryRootUrl = info.repositoryRootURL
      try {
        def svnPath = SvnPath.parse info.URL
        if (svnPath.trunk) {
          result.trunk = true
          result.name = "trunk"
          log.info "Working copy is on trunk at revision $result.revisionNumber"
        } else if (svnPath.branch) {
          result.branch = true
          result.name = svnPath.branchName
          log.info "Working copy is on branch $result.branch at revision $result.revisionNumber"
        } else if (svnPath.tag) {
          result.tag = true
          result.name = svnPath.tagName
          log.info "Working copy is on tag $result.tag at revision $result.revisionNumber"
        }
      } catch (MalformedURLException e) {
        log.warning "Working copy must be a trunk, branches or tags folder: $e.message"
      }
    } catch (Exception e) {
      if (ignoreErrors) {
        log.warning "Could not execute svn-info on $srcPath.absolutePath ($e.message)"
      } else {
        throw new InvalidUserDataException("Could not execute svn-info on $srcPath.absolutePath ($e.message)", e)
      }
    }
    return result
  }

  static SVNRevision revisionFrom(Long value) {
    return value != null && value >= 0 ? SVNRevision.create(value) : SVNRevision.HEAD
  }
}
