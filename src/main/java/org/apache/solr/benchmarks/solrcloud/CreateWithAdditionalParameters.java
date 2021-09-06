package org.apache.solr.benchmarks.solrcloud;

import java.util.Map;

import org.apache.solr.client.solrj.request.CollectionAdminRequest.Create;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;

public class CreateWithAdditionalParameters extends Create {
	private CreateWithAdditionalParameters(String collection, String config, Integer numShards, Integer numNrtReplicas,
			Integer numTlogReplicas, Integer numPullReplicas) {
		super(collection, config, numShards, numNrtReplicas, numTlogReplicas, numPullReplicas);
	}

	Create cr;
	Map<String, String> additionalParams;
	public CreateWithAdditionalParameters(Create cr, String collection, Map<String, String> additionalParams) {
		super(collection, cr.getConfigName(), cr.getNumShards(), cr.getNumNrtReplicas(), cr.getNumTlogReplicas(), cr.getNumPullReplicas());
		this.cr = cr;
		this.additionalParams = additionalParams;
	}

	@Override
	public SolrParams getParams() {
		ModifiableSolrParams params = (ModifiableSolrParams)cr.getParams();
		if (additionalParams != null) {
			for (String k: additionalParams.keySet()) {
				params.set(k, additionalParams.get(k));
			}
		}
		return params;
	}

	@Override
	public void setAsyncId(String asyncId) {
		// TODO Auto-generated method stub
		super.setAsyncId(asyncId);;
	}

	@Override
	public String getAsyncId() {
		return super.asyncId;
	}

	@Override
	public String getCollection() {
		// TODO Auto-generated method stub
		return cr.getCollection();
	}
	
	@Override
	public String getCreateNodeSet() {
		// TODO Auto-generated method stub
		return cr.getCreateNodeSet();
	}

}