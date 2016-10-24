/**
 * Copyright 2016 Twitter. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.graphjet.algorithms.counting.recommendationGenerator;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import com.google.common.collect.Lists;
import com.twitter.graphjet.algorithms.*;
import com.twitter.graphjet.algorithms.RecommendationInfo;
import com.twitter.graphjet.algorithms.RecommendationInfoUser;
import com.twitter.graphjet.algorithms.counting.request.TopSecondDegreeByCountRequestForUser;
import com.twitter.graphjet.hashing.SmallArrayBasedLongToDoubleMap;
import it.unimi.dsi.fastutil.longs.LongList;

public class TopSecondDegreeByCountUserRecsGenerator {

  /**
   * Generate a list of recommendations based on given list of candidate nodes and the original request
   * @param request         original request message, contains filtering criteria
   * @param candidateNodes  list of candidate nodes
   * @return                list of {@link RecommendationInfoUser}
   */
  public static List<RecommendationInfo> generateUserRecs(
      TopSecondDegreeByCountRequestForUser request,
      List<NodeInfo> candidateNodes) {

    // Recommend at most 100 users
    int maxNumResults = Math.min(request.getMaxNumResults(),
        RecommendationRequest.DEFAULT_RECOMMENDATION_RESULTS);

    PriorityQueue<NodeInfo> qualifiedNodes =
        getQualifiedNodes(candidateNodes, request.getMinUserPerSocialProof(), maxNumResults);

    return getRecommendationsFromNodes(request, qualifiedNodes);
  }

  private static PriorityQueue<NodeInfo> getQualifiedNodes(
      List<NodeInfo> nodeInfoList,
      Map<Byte, Integer> minSocialProofSizes,
      int maxNumResults) {
    PriorityQueue<NodeInfo> topResults = new PriorityQueue<>(maxNumResults);

    for (NodeInfo nodeInfo : nodeInfoList) {
      if (isQualifiedSocialProof(minSocialProofSizes, nodeInfo.getSocialProofs())) {
        GeneratorHelper.addResultToPriorityQueue(topResults, nodeInfo, maxNumResults);
      }
    }
    return topResults;
  }

  private static boolean isQualifiedSocialProof(
      Map<Byte, Integer> minSocialProofSizes,
      SmallArrayBasedLongToDoubleMap[] socialProofs) {
    for (int i = 0; i < socialProofs.length; i++) {
      byte proofType = (byte)i;
      if (!minSocialProofSizes.containsKey(proofType)) {
        // if there is no limit on social proof size, qualified
        continue;
      }
      if (socialProofs[proofType] == null) {
        // if node does not have this type of social proof, not qualified
        return false;
      }
      if (socialProofs[proofType].size() < minSocialProofSizes.get(proofType)) {
        // if number of social proofs below threashold, not qualified
        return false;
      }
    }
    return true;
  }

  private static  List<RecommendationInfo> getRecommendationsFromNodes(
      TopSecondDegreeByCountRequestForUser request,
      PriorityQueue<NodeInfo> topNodes) {
    List<RecommendationInfo> outputResults = Lists.newArrayListWithCapacity(topNodes.size());
    byte[] validSocialProofs = request.getSocialProofTypes();
    int maxSocialProofSize = request.getMaxSocialProofTypeSize();

    while (!topNodes.isEmpty()) {
      NodeInfo nodeInfo = topNodes.poll();

      Map<Byte, LongList> topSocialProofs = GeneratorHelper.pickTopSocialProofs(
          nodeInfo.getSocialProofs(),
          validSocialProofs,
          maxSocialProofSize);

      RecommendationInfoUser userRecs = new RecommendationInfoUser(
          nodeInfo.getValue(),
          nodeInfo.getWeight(),
          topSocialProofs);
      outputResults.add(userRecs);
    }
    Collections.reverse(outputResults);
    return outputResults;
  }
}
