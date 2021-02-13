/*
 * Copyright 2020, Seqera Labs
 * Copyright 2013-2019, Centre for Genomic Regulation (CRG)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nextflow.script

import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import groovy.util.logging.Slf4j
import groovyx.gpars.dataflow.DataflowReadChannel
import groovyx.gpars.dataflow.expression.DataflowExpression
import nextflow.Channel
import nextflow.extension.CH

/**
 * Wrap a process or workflow output
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class TestflowDsl {

    private List<EmissionValues> emissions = new ArrayList<>()
    private int outputsCount

    TestflowDsl(ChannelOut outputs) {
        init(outputs)
    }

    private void init(ChannelOut outputs) {
        this.outputsCount = outputs.size()
        //
        List<ChannelEntry> channels = readable(outputs)
        boolean single=true
        boolean terminated=false
        for( int i=0; !terminated && channels.size()>0; i++ ) {
            def values = new ArrayList()
            def named = new HashMap()
            for( int j=0; !terminated && j<channels.size(); j++ ) {
                final x = channels[j].read()
                if( Channel.STOP.is(x) ) {
                    terminated=true
                    break
                }
                values[j] = x
                if( channels[j].name )
                    named[ channels[j].name ] = x
                if( i==0 )
                    single &= channels[j].isValue()
            }
            //
            if( !terminated ) {
                emissions << new EmissionValues(values, named)
            }
            if( single ) {
                // if channel are all dataflow variables stop at the first iteration
                break
            }
        }
    }

    private List<ChannelEntry> readable(ChannelOut output ) {
        final result = new ArrayList(output.size())
        for( int i=0; i<output.size(); i++ ) {
            final ch = output.get(i)
            final name = output.nameOf(ch)
            final read = CH.getReadChannel(ch)
            result << new ChannelEntry(i, name, read)
        }
        return result
    }

    int outputsCount() {
        return outputsCount
    }

    int emissionsCount() {
        return emissions.size()
    }

    void withEmission(int index, Closure body) {
        final values = emissions.get(index)
        final ctx = [out: values]
        body.cloneWith(ctx).call()
    }

    @TupleConstructor(includeFields = true)
    static class ChannelEntry {
        private int index
        private String name
        private DataflowReadChannel channel

        int getIndex() { index }
        String getName() { name }
        boolean isValue() { channel instanceof DataflowExpression }
        def read() { channel.getVal() }
    }

    @TupleConstructor(includeFields = true)
    static class EmissionValues {
        private List values
        private Map<String,Object> named

        int size() { return values.size() }
        int getLength() { values.size() }
        Object getAt(int index) { values[index] }

        @Override
        def getProperty(String name) {
            this.named.containsKey(name)
                ? named.get(name)
                : metaClass.getProperty(this,name)
        }
    }
}
