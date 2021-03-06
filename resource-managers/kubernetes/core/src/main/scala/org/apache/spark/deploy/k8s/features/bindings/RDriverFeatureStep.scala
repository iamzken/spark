/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.spark.deploy.k8s.features.bindings

import scala.collection.JavaConverters._

import io.fabric8.kubernetes.api.model.{ContainerBuilder, EnvVarBuilder, HasMetadata}

import org.apache.spark.deploy.k8s.{KubernetesConf, KubernetesDriverSpecificConf, KubernetesUtils, SparkPod}
import org.apache.spark.deploy.k8s.Constants._
import org.apache.spark.deploy.k8s.features.KubernetesFeatureConfigStep

private[spark] class RDriverFeatureStep(
  kubernetesConf: KubernetesConf[KubernetesDriverSpecificConf])
  extends KubernetesFeatureConfigStep {
  override def configurePod(pod: SparkPod): SparkPod = {
    val roleConf = kubernetesConf.roleSpecificConf
    require(roleConf.mainAppResource.isDefined, "R Main Resource must be defined")
    // Delineation is done by " " because that is input into RRunner
    val maybeRArgs = Option(roleConf.appArgs).filter(_.nonEmpty).map(
      rArgs =>
        new EnvVarBuilder()
          .withName(ENV_R_ARGS)
          .withValue(rArgs.mkString(" "))
          .build())
    val envSeq =
      Seq(new EnvVarBuilder()
            .withName(ENV_R_PRIMARY)
            .withValue(KubernetesUtils.resolveFileUri(kubernetesConf.sparkRMainResource().get))
          .build())
    val rEnvs = envSeq ++
      maybeRArgs.toSeq

    val withRPrimaryContainer = new ContainerBuilder(pod.container)
        .addAllToEnv(rEnvs.asJava)
        .addToArgs("driver-r")
        .addToArgs("--properties-file", SPARK_CONF_PATH)
        .addToArgs("--class", roleConf.mainClass)
      .build()

    SparkPod(pod.pod, withRPrimaryContainer)
  }
  override def getAdditionalPodSystemProperties(): Map[String, String] = Map.empty

  override def getAdditionalKubernetesResources(): Seq[HasMetadata] = Seq.empty
}
