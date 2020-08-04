import org.apache.solr.benchmarks.solrcloud.LocalSolrNode;
import org.apache.solr.benchmarks.solrcloud.SolrCloud;
import org.apache.solr.benchmarks.solrcloud.SolrNode;

public class RestartSolrTask {
	final SolrCloud cluster;
	public RestartSolrTask(SolrCloud cluster) {
		this.cluster = cluster;
	}
	
	public void restart(SolrNode node) throws Exception {
		if (node instanceof LocalSolrNode) {
			LocalSolrNode localNode = (LocalSolrNode) node;
			
			localNode.restart();
		} else {
			throw new UnsupportedOperationException("Can't restart a node of instance " + node.getClass());
		}
	}
}
