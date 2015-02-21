package org.zoodb.index.critbit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class TestCritBit64COW {

    private CritBit64COW<Integer> newCritBit() {
        return CritBit64COW.create();
    }

    @Test
    public void testInsertDelete_OK() {
        //make sure the operations work
        CritBit64COW<Integer> tree = newCritBit();
        tree.iterator();
        int size = 100;

        for (int i = 0; i < size; i++) {
            tree.put(i, i);
        }
        for (int i = 0; i < size; i++) {
            assertTrue(tree.contains(i));
        }
        for (int i = 0; i < size; i++) {
            tree.remove(i);
            assertFalse(tree.contains(i));
        }
        for (int i = 0; i < size; i++) {
            assertFalse(tree.contains(i));
        }
    }

    @Test
    public void testIteratorConsistency_Insert_Empty() {
        //make an empty tree
        CritBit64COW<Integer> tree = newCritBit();

        //get an iteraotr
        CritBit64COW.CBIterator<Integer> it = tree.iterator();

        //add some entries
        int size = 100;
        for (int i = 0; i < size; i++) {
            tree.put(i, i);
        }

        //make sure entries are there
        for (int i = 0; i < size; i++) {
            assertTrue(tree.contains(i));
        }

        //iterator should see the tree as empty
        int nrEntries = 0;
        while (it.hasNext()) {
            it.next();
            nrEntries++;
        }

        assertEquals(0, nrEntries);
    }

    @Test
    public void testIteratorConsistency_Insert_OneEntry() {
        //make a tree with one entry
        CritBit64COW<Integer> tree = newCritBit();
        tree.put(-1, -1);

        //get an iterator
        CritBit64COW.CBIterator<Integer> it = tree.iterator();

        //add some data
        int size = 100;
        for (int i = 0; i < size; i++) {
            tree.put(i, i);
        }

        //make sure data is there
        for (int i = 0; i < size; i++) {
            assertTrue(tree.contains(i));
        }

        //iterator should only see the original entry
        int nrEntries = 0;
        while (it.hasNext()) {
            it.next();
            nrEntries++;
        }

        assertEquals(1, nrEntries);
    }

    @Test
    public void testIteratorConsistency_Insert_MultipleEntries() {
        //make a tree and put some data into it
        CritBit64COW<Integer> tree = newCritBit();

        Map<Long, Integer> initial = new LinkedHashMap<>();
        int size = 100;
        for (int i = -size - 1; i < 0; i++) {
            tree.put(i, i);
            initial.put((long) i, i);
        }

        //get an iterator
        CritBit64COW.CBIterator<Integer> it = tree.iterator();

        //add some more data
        for (int i = 0; i < size; i++) {
            tree.put(i, i);
        }

        //make sure all data is there
        for (int i = -size - 1; i < size; i++) {
            assertTrue(tree.contains(i));
        }

        //the old iterator should only see the old data
        Map<Long, Integer> after = getEntries(it);
        assertEquals(initial, after);
    }

    @Test
    public void testIteratorConsistency_Delete_1Entry() {
        //make a tree with one entry
        CritBit64COW<Integer> tree = newCritBit();
        tree.put(1, 1);

        //get an iterator
        CritBit64COW.CBIterator<Integer> it = tree.iterator();

        //remove the entry
        tree.remove(1);

        //iterator should sill see the entry there
        CritBit64COW.Entry<Integer> e = it.nextEntry();
        assertEquals(1L, e.key());
        assertEquals(new Integer(1), e.value());
        assertFalse(it.hasNext());
    }

    @Test
    public void testIteratorConsistency_Delete_MultipleEntries() {
        //make a tree and add some entries to it
        CritBit64COW<Integer> tree = newCritBit();

        Map<Long, Integer> all = new LinkedHashMap<>();
        Map<Long, Integer> remaining = new LinkedHashMap<>();
        int size = 100;
        int key;
        for (int i = 0; i < size; i++) {
            key = (2 * i);

            remaining.put((long) key, key);
            all.put((long) key, key);
            tree.put(key, key);

            key = (2 * i) + 1;
            all.put((long) key, key);
            tree.put(key, key);
        }

        //get an iterator
        CritBit64COW.CBIterator<Integer> it = tree.iterator();

        //remove some of the entries
        for (int i = 0; i < size; i++) {
            tree.remove(2 * i + 1);
        }

        //make sure the old iterator doesn't see changes
        Map<Long, Integer> after = getEntries(it);
        assertEquals(after, all);

        //make sure a new iterator would see the changes
        after = getEntries(tree);
        assertEquals(after, remaining);
    }

    @Test
    public void testCopy_Insert() {
        //create a tree and make a copy of it
        CritBit64COW<Integer> tree = newCritBit();
        CritBit64COW<Integer> copy = tree.copy();

        //fill original with entries
        Map<Long, Integer> entries = new LinkedHashMap<>();
        int size = 100;
        for (int i = 0; i < size; i++) {
            tree.put(i, i);
            entries.put((long) i, i);
        }

        //make sure copy is empty
        assertTrue(getEntries(copy).isEmpty());

        //make sure all entries are in the original tree
        assertEquals(entries, getEntries(tree));
    }

    @Test
    public void testCopy_Delete() {
        //create a tree and fill it with entries
        CritBit64COW<Integer> tree = newCritBit();
        Map<Long, Integer> entries = new LinkedHashMap<>();
        int size = 100;
        for (int i = 0; i < size; i++) {
            tree.put(i, i);
            entries.put((long) i, i);
        }

        //make a copy
        CritBit64COW<Integer> copy = tree.copy();

        //remove all entries from original tree
        for (int i = 0; i < size; i++) {
            tree.remove(i);
        }

        //check the copy has all the entries
        assertEquals(entries, getEntries(copy));

        //check that original is empty
        assertTrue(getEntries(tree).isEmpty());
    }

    private Map<Long, Integer> getEntries(CritBit64COW<Integer> tree) {
        return getEntries(tree.iterator());
    }

    private Map<Long, Integer> getEntries(CritBit64COW.CBIterator<Integer> it) {
        Map<Long, Integer> entries = new LinkedHashMap<>();
        CritBit64COW.Entry<Integer> e;
        while (it.hasNext()) {
            e = it.nextEntry();
            entries.put(e.key(), e.value());
        }
        return entries;
    }
    
    private static class Writer implements Runnable {
    	private final int start;
    	private final int n;
    	private final CritBit64COW<Integer> tree;

    	Writer(CritBit64COW<Integer> tree, int start, int n) {
    		this.tree = tree;
    		this.start = start;
    		this.n = n;
    	}    	

    	@Override
    	public void run() {
			for (int i = start; i < start+n; i++) {
				tree.put(i, i);
			}
    	}
    }
    
    @Test
    public void testMultiThreading() throws InterruptedException {
        CritBit64COW<Integer> tree = newCritBit();

        int N_W = 10; //threads
        int N = 1*1000*1000;
        
        ExecutorService es = Executors.newFixedThreadPool(N_W);
    	int batchSize = N/N_W;
        for (int i = 0; i < N_W; i++) {
        	es.execute( new Writer(tree, i*batchSize, batchSize) );
		}
        es.shutdown();
        es.awaitTermination(10, TimeUnit.SECONDS);
		
        if (!es.isTerminated()) {
        	fail();
        }
		
        for (int i = 0; i < N; i++) {
            assertTrue(tree.contains(i));
        }
        for (int i = 0; i < N; i++) {
            assertEquals(i, (int)tree.remove(i));
        }
        for (int i = 0; i < N; i++) {
            assertFalse(tree.contains(i));
        }
    }


}