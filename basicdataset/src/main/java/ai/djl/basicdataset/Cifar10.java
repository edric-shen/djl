/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance
 * with the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package ai.djl.basicdataset;

import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.Artifact;
import ai.djl.repository.MRL;
import ai.djl.repository.Repository;
import ai.djl.repository.dataset.ZooDataset;
import ai.djl.training.dataset.ArrayDataset;
import ai.djl.translate.Pipeline;
import ai.djl.util.Utils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * CIFAR10 image classification dataset from https://www.cs.toronto.edu/~kriz/cifar.html Each sample
 * is an image (in 3D NDArray) with shape (32, 32, 3).
 */
public final class Cifar10 extends ArrayDataset implements ZooDataset {

    private static final String ARTIFACT_ID = "cifar10";
    // 3072 = 32 * 32 * 3, i.e. one image size, +1 here is label
    private static final int DATA_AND_LABEL_SIZE = 32 * 32 * 3 + 1;

    private NDManager manager;
    private Repository repository;
    private Artifact artifact;
    private Usage usage;
    private boolean prepared;

    Cifar10(Builder builder) {
        super(builder);
        this.manager = builder.manager;
        this.repository = builder.repository;
        this.artifact = builder.artifact;
        this.usage = builder.usage;
    }

    public static Builder builder(NDManager manager) {
        return new Builder().setManager(manager);
    }

    /** {@inheritDoc} */
    @Override
    public MRL getMrl() {
        return new MRL(MRL.Dataset.CV, BasicDatasets.GROUP_ID, ARTIFACT_ID);
    }

    /** {@inheritDoc} */
    @Override
    public Repository getRepository() {
        return repository;
    }

    /** {@inheritDoc} */
    @Override
    public Artifact getArtifact() {
        return artifact;
    }

    /** {@inheritDoc} */
    @Override
    public Usage getUsage() {
        return usage;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPrepared() {
        return prepared;
    }

    /** {@inheritDoc} */
    @Override
    public void setPrepared(boolean prepared) {
        this.prepared = prepared;
    }

    /** {@inheritDoc} */
    @Override
    public void useDefaultArtifact() throws IOException {
        artifact = repository.resolve(getMrl(), "1.0", null);
    }

    /** {@inheritDoc} */
    @Override
    public void prepareData(Usage usage) throws IOException {
        Map<String, Artifact.Item> map = artifact.getFiles();
        Artifact.Item item;
        switch (usage) {
            case TRAIN:
                item = map.get("data_batch.bin");
                break;
            case TEST:
                item = map.get("test_batch.bin");
                break;
            case VALIDATION:
            default:
                throw new UnsupportedOperationException("Validation data not available.");
        }
        NDArray dataAndLabels = readData(item);
        data =
                new NDArray[] {
                    dataAndLabels.get(":, 1:").reshape(-1, 3, 32, 32).transpose(0, 2, 3, 1)
                };
        labels = new NDArray[] {dataAndLabels.get(":,0")};
        // check if data and labels have the same size
        if (data[0].size(0) != labels[0].size(0)) {
            throw new IOException(
                    "the size of data "
                            + data[0].size(0)
                            + " didn't match with the size of labels "
                            + labels[0].size(0));
        }
        size = labels[0].size();
    }

    private NDArray readData(Artifact.Item item) throws IOException {
        try (InputStream is = repository.openStream(item, null)) {
            byte[] buf = Utils.toByteArray(is);
            int length = buf.length / DATA_AND_LABEL_SIZE;
            try (NDArray array =
                    manager.create(new Shape(length, DATA_AND_LABEL_SIZE), DataType.UINT8)) {
                array.set(buf);
                return array.asType(DataType.FLOAT32, true);
            }
        }
    }

    public static final class Builder extends BaseBuilder<Builder> {

        private NDManager manager;
        private Repository repository;
        private Artifact artifact;
        private Usage usage;

        public Builder() {
            repository = BasicDatasets.REPOSITORY;
            usage = Usage.TRAIN;
            pipeline = new Pipeline(new ToTensor());
        }

        /** {@inheritDoc} */
        @Override
        protected Builder self() {
            return this;
        }

        public Builder setManager(NDManager manager) {
            this.manager = manager;
            return this;
        }

        public Builder optRepository(Repository repository) {
            this.repository = repository;
            return this;
        }

        public Builder optArtifact(Artifact artifact) {
            this.artifact = artifact;
            return this;
        }

        public Builder optUsage(Usage usage) {
            this.usage = usage;
            return this;
        }

        public Cifar10 build() {
            return new Cifar10(this);
        }
    }
}
