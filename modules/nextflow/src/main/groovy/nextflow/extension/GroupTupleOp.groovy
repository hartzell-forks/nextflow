/*
 * Copyright 2020-2022, Seqera Labs
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

package nextflow.extension

import groovy.util.logging.Slf4j
import groovyx.gpars.dataflow.DataflowReadChannel
import groovyx.gpars.dataflow.DataflowWriteChannel
import nextflow.Channel
import nextflow.util.ArrayBag
import nextflow.util.CacheHelper
import nextflow.util.CheckHelper
/**
 * Implements {@link OperatorImpl#groupTuple} operator logic
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
class GroupTupleOp extends AbstractGroupOp {

    private List indices

    private Map<List,List> groups = [:]

    GroupTupleOp(Map params, DataflowReadChannel source) {

        super(params, source)

        CheckHelper.checkParams('groupTuple', params, PARAM_TYPES + [ by: [Integer, List] ])

        indices = getIndices(params?.by)
    }

    GroupTupleOp setTarget(DataflowWriteChannel target) {
        this.@target = target
        return this
    }

    static private List<Integer> getIndices( by ) {

        if( by == null )
            return [0]

        if( by instanceof List )
            return by as List<Integer>

        if( by instanceof Integer || by.toString().isInteger() )
            return [by as Integer]

        throw new IllegalArgumentException("Not a valid `by` index for `groupTuple` operator: '${by}' -- It must be an integer value or a list of integers")
    }

    @Override
    protected Map getHandlers() {
        [onNext: this.&onNext, onComplete: this.&onComplete]
    }

    /**
     * Collect a received item into its group.
     *
     * @param item
     */
    private void onNext(List item) {

        // get the grouping key
        final key = item[indices]
        final len = item.size()

        // get the group for the specified key
        // or create it if it does not exist
        final List group = groups.getOrCreate(key) {
            def result = new ArrayList(len)
            for( int i=0; i<len; i++ )
                result[i] = (i in indices ? item[i] : new ArrayBag())
            return result
        }

        // append the values in the item
        int count = -1
        for( int i=0; i<len; i++ ) {
            if( i !in indices ) {
                def list = (List)group[i]
                list.add( item[i] )
                count = list.size()
            }
        }

        // emit group if it is complete
        final size = this.size ?: sizeBy(key)
        if( size > 0 && size == count ) {
            bindGroup(group, size)
            groups.remove(key)
        }
    }

    /**
     * Emit the remaining groups when all values have been received.
     */
    private void onComplete(nop) {
        groups.each { key, group -> bindGroup(group, size ?: sizeBy(key)) }
        target.bind(Channel.STOP)
    }

    /**
     * Emit a group.
     *
     * @param group
     * @param size
     */
    private void bindGroup( List group, int size ) {

        def item = new ArrayList(group)

        if( !remainder && size > 0 ) {
            // make sure the group contains 'size' elements
            List list = group.find { it instanceof List }
            if( list.size() != size ) {
                return
            }
        }

        // sort the grouped entries
        if( comparator )
            for( def entry : item )
                if( entry instanceof List )
                    Collections.sort((List)entry, comparator)

        target.bind( item )
    }

}
