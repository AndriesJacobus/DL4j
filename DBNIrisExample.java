package org.deeplearning4j.examples.Saving;


import org.deeplearning4j.datasets.iterator.DataSetIterator;
import org.deeplearning4j.datasets.iterator.impl.IrisDataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.RBM;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.params.DefaultParamInitializer;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.SplitTestAndTrain;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.Random;


/**
 * Created by agibsonccc on 9/12/14.
 * Edited by AndriesJacobus on 8/9/15
 */
public class DBNIrisExample {

    private static Logger log = LoggerFactory.getLogger(DBNIrisExample.class);

    public static void main(String[] args) throws IOException {
        // Customizing params
        Nd4j.MAX_SLICES_TO_PRINT = -1;
        Nd4j.MAX_ELEMENTS_PER_SLICE = -1;

        final int numRows = 4;
        final int numColumns = 1;
        int outputNum = 3;
        int numSamples = 150;
        int batchSize = 150;
        int iterations = 5;
        int splitTrainNum = (int) (batchSize * .8);
        int seed = 123;
        int listenerFreq = 1;

        log.info("Load data....");
        DataSetIterator iter = new IrisDataSetIterator(batchSize, numSamples);
        DataSet next = iter.next();
        next.normalizeZeroMeanZeroUnitVariance();

        log.info("Split data....");
        SplitTestAndTrain testAndTrain = next.splitTestAndTrain(splitTrainNum, new Random(seed));
        DataSet train = testAndTrain.getTrain();
        DataSet test = testAndTrain.getTest();
        Nd4j.ENFORCE_NUMERICAL_STABILITY = true;

        log.info("Build model....");

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(seed) // Seed to lock in weight initialization for tuning
                .iterations(iterations) // # training iterations predict/classify & backprop
                .learningRate(1e-6f) // Optimization step size
                .optimizationAlgo(OptimizationAlgorithm.CONJUGATE_GRADIENT) // Backprop method (calculate the gradients)
                .l1(1e-1).regularization(true).l2(2e-4)
                .useDropConnect(true)
                .list(2) // # NN layers (does not count input layer)
                .layer(0, new RBM.Builder(RBM.HiddenUnit.RECTIFIED, RBM.VisibleUnit.GAUSSIAN)
                                .nIn(numRows * numColumns) // # input nodes
                                .nOut(3) // # fully connected hidden layer nodes. Add list if multiple layers.
                                .weightInit(WeightInit.XAVIER) // Weight initialization method
                                .k(1) // # contrastive divergence iterations
                                .activation("relu") // Activation function type
                                .lossFunction(LossFunctions.LossFunction.RMSE_XENT) // Loss function type
                                .updater(Updater.ADAGRAD)
                                .dropOut(0.5)
                                .build()
                ) // NN layer type
                .layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                                .nIn(3) // # input nodes
                                .nOut(outputNum) // # output nodes
                                .activation("softmax")
                                .build()
                ) // NN layer type
                .build();

        MultiLayerNetwork model = new MultiLayerNetwork(conf);

        model.init();
//        model.setListeners(Arrays.asList(new ScoreIterationListener(listenerFreq),
//                new GradientPlotterIterationListener(listenerFreq),
//                new LossPlotterIterationListener(listenerFreq)));

        model.setListeners(Arrays.asList((IterationListener) new ScoreIterationListener(listenerFreq)));

        log.info("Train model....");
        model.fit(train);

        //SAVE DATA
        //^^turn model's weight matrix into vector
        INDArray params = model.params();

        String confS = conf.toJson();

        //erase old data for CONF
        PrintWriter pWriter = new PrintWriter("C:/conf.json");
        pWriter.print("");
        pWriter.close();

        //write new data for CONF
        FileWriter o = new FileWriter("C:/conf.json", true);
        BufferedWriter fw = new BufferedWriter(o);
        fw.write(confS);
        fw.close();
        o.close();

        //write new data for PARAMS
        Nd4j.write(params, new DataOutputStream(new FileOutputStream("C:/params.json")));      //^^save vector as a string to file type of your choice

        //LOAD DATA
        String cc = readFile("C:/conf.json");
        MultiLayerConfiguration confA = MultiLayerConfiguration.fromJson(cc);

        MultiLayerNetwork model1 = new MultiLayerNetwork(confA);

        //PROBLEM:
        INDArray params1 = Nd4j.read(new DataInputStream(new FileInputStream("C:/params.json")));

        //JOptionPane.showMessageDialog(null, "Param saved: \n" + params.toString() + "\n\nParam load: \n" + params1.toString());

        //JOptionPane.showMessageDialog(null, "Mod saved type: \n" + model.type().toString() + "\n\nMod load type: \n" + model1.type().toString());

        model1.setParams(params1);

        log.info("Evaluate weights....");
        for(org.deeplearning4j.nn.api.Layer layer : model.getLayers()) {
            INDArray w = layer.getParam(DefaultParamInitializer.WEIGHT_KEY);
            log.info("Weights: " + w);
        }

        log.info("Evaluate model....");
        Evaluation eval = new Evaluation(outputNum);
        INDArray output = model.output(test.getFeatureMatrix());

        for (int i = 0; i < output.rows(); i++) {
            String actual = train.getLabels().getRow(i).toString().trim();
            String predicted = output.getRow(i).toString().trim();
            log.info("actual " + actual + " vs predicted " + predicted);
        }

        eval.eval(test.getLabels(), output);
        log.info(eval.stats());
        log.info("****************Example finished********************");

    }

    //FileReader
    static String readFile(String fileName) throws IOException
    {
        BufferedReader br = new BufferedReader(new FileReader(fileName));
        try
        {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null)
            {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }

            return sb.toString();
        }
        finally
        {
            br.close();
        }
    }
}