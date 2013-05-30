/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.ql.exec.vector.expressions.aggregates.gen;

import java.util.ArrayList;

import org.apache.hadoop.hive.ql.exec.Description;
import org.apache.hadoop.hive.ql.exec.vector.expressions.VectorExpression;
import org.apache.hadoop.hive.ql.exec.vector.expressions.aggregates.VectorAggregateExpression;
import org.apache.hadoop.hive.ql.exec.vector.expressions.aggregates.
    VectorAggregateExpression.AggregationBuffer;
import org.apache.hadoop.hive.ql.exec.vector.VectorAggregationBufferRow;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.DoubleColumnVector;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.StructField;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.DoubleObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.LongObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorUtils;

/**
* VectorUDAFCountLong. Vectorized implementation for COUNT aggregates. 
*/
@Description(name = "count", value = "_FUNC_(expr) - Returns the maximum value of expr (vectorized, type: long)")
public class VectorUDAFCountLong extends VectorAggregateExpression {
    
    /** 
    /* class for storing the current aggregate value.
    */
    static class Aggregation implements AggregationBuffer {
      long value;
      boolean isNull;
      
      public void initIfNull() {
        if (isNull) {
          isNull = false;
          value = 0;
        }
      }
    }
    
    private VectorExpression inputExpression;
  private LongWritable result;    
    
    public VectorUDAFCountLong(VectorExpression inputExpression) {
      super();
      this.inputExpression = inputExpression;
      result = new LongWritable(0);
    }

    private Aggregation getCurrentAggregationBuffer(
        VectorAggregationBufferRow[] aggregationBufferSets,
        int aggregateIndex,
        int row) {
      VectorAggregationBufferRow mySet = aggregationBufferSets[row];
      Aggregation myagg = (Aggregation) mySet.getAggregationBuffer(aggregateIndex);
      return myagg;
    }
    
    @Override
    public void aggregateInputSelection(
      VectorAggregationBufferRow[] aggregationBufferSets,
      int aggregateIndex, 
      VectorizedRowBatch batch) throws HiveException {
      
      int batchSize = batch.size;
      
      if (batchSize == 0) {
        return;
      }

      inputExpression.evaluate(batch);
      
      LongColumnVector inputVector = (LongColumnVector)batch.
        cols[this.inputExpression.getOutputColumn()];

      if (inputVector.noNulls) {
          // if there are no nulls then the iteration is the same on all cases
          iterateNoNullsWithAggregationSelection(
            aggregationBufferSets, aggregateIndex, batchSize);
      } else if (!batch.selectedInUse) {
          iterateHasNullsWithAggregationSelection(
            aggregationBufferSets, aggregateIndex, 
            batchSize, inputVector.isNull);
      } else if (batch.selectedInUse) {
          iterateHasNullsSelectionWithAggregationSelection(
            aggregationBufferSets, aggregateIndex, 
            batchSize, batch.selected, inputVector.isNull);
      }
    }
    
    private void iterateNoNullsWithAggregationSelection(
        VectorAggregationBufferRow[] aggregationBufferSets,
        int aggregateIndex, 
        int batchSize) {
        
        for (int i=0; i < batchSize; ++i) {
          Aggregation myagg = getCurrentAggregationBuffer(
            aggregationBufferSets, 
            aggregateIndex,
            i);
          myagg.initIfNull();
          myagg.value++;
        }
    }

    private void iterateHasNullsWithAggregationSelection(
        VectorAggregationBufferRow[] aggregationBufferSets,
        int aggregateIndex, 
        int batchSize,
        boolean[] isNull) {
        
        for (int i=0; i < batchSize; ++i) {
          if (!isNull[i]) {
            Aggregation myagg = getCurrentAggregationBuffer(
              aggregationBufferSets, 
              aggregateIndex,
              i);
            myagg.initIfNull();
            myagg.value++;
          }
        }
    }

    private void iterateHasNullsSelectionWithAggregationSelection(
        VectorAggregationBufferRow[] aggregationBufferSets,
        int aggregateIndex, 
        int batchSize,
        int[] selection,
        boolean[] isNull) {
        
        for (int j=0; j < batchSize; ++j) {
          int i = selection[j];
          if (!isNull[i]) {
            Aggregation myagg = getCurrentAggregationBuffer(
              aggregationBufferSets, 
              aggregateIndex,
              j);
            myagg.initIfNull();
            myagg.value++;
          }
        }
    }

    
    @Override
    public void aggregateInput(AggregationBuffer agg, VectorizedRowBatch batch) 
    throws HiveException {
      
      inputExpression.evaluate(batch);
      
      LongColumnVector inputVector = (LongColumnVector)batch.
        cols[this.inputExpression.getOutputColumn()];
      
      int batchSize = batch.size;
      
      if (batchSize == 0) {
        return;
      }
      
      Aggregation myagg = (Aggregation)agg;

      myagg.initIfNull();

      if (inputVector.isRepeating) {
        if (inputVector.noNulls || !inputVector.isNull[0]) {
          myagg.value += batchSize;
        }
        return;
      }
      
      if (inputVector.noNulls) {
        myagg.value += batchSize;
        return;
      }
      else if (!batch.selectedInUse) {
        iterateNoSelectionHasNulls(myagg, batchSize, inputVector.isNull);
      }
      else {
        iterateSelectionHasNulls(myagg, batchSize, inputVector.isNull, batch.selected);
      }
    }
  
    private void iterateSelectionHasNulls(
        Aggregation myagg, 
        int batchSize,
        boolean[] isNull, 
        int[] selected) {
      
      for (int j=0; j< batchSize; ++j) {
        int i = selected[j];
        if (!isNull[i]) {
          myagg.value += 1;
        }
      }
    }

    private void iterateNoSelectionHasNulls(
        Aggregation myagg, 
        int batchSize, 
        boolean[] isNull) {
      
      for (int i=0; i< batchSize; ++i) {
        if (!isNull[i]) {
          myagg.value += 1;
        }
      }
    }

    @Override
    public AggregationBuffer getNewAggregationBuffer() throws HiveException {
      return new Aggregation();
    }

    @Override
    public void reset(AggregationBuffer agg) throws HiveException {
      Aggregation myAgg = (Aggregation) agg;
      myAgg.isNull = true;
    }

    @Override
    public Object evaluateOutput(AggregationBuffer agg) throws HiveException {
    Aggregation myagg = (Aggregation) agg;
      if (myagg.isNull) {
      return null;    
      }
      else {
        result.set (myagg.value);
      return result;
      }
    }

    @Override
    public ObjectInspector getOutputObjectInspector() {
      return PrimitiveObjectInspectorFactory.writableLongObjectInspector;
    }

}
