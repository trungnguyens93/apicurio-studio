/*
 * Copyright 2017 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package test.io.apicurio.hub.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import io.apicurio.hub.api.beans.ApiContentType;
import io.apicurio.hub.api.beans.ApiDesign;
import io.apicurio.hub.api.beans.ApiDesignCommand;
import io.apicurio.hub.api.beans.ApiDesignContent;
import io.apicurio.hub.api.beans.Collaborator;
import io.apicurio.hub.api.beans.LinkedAccount;
import io.apicurio.hub.api.beans.LinkedAccountType;
import io.apicurio.hub.api.exceptions.AlreadyExistsException;
import io.apicurio.hub.api.exceptions.NotFoundException;
import io.apicurio.hub.api.storage.IStorage;
import io.apicurio.hub.api.storage.StorageException;

/**
 * @author eric.wittmann@gmail.com
 */
public class MockStorage implements IStorage {
    
    private Map<String, Map<LinkedAccountType, LinkedAccount>> accounts = new HashMap<>();
    private Map<String, ApiDesign> designs = new HashMap<>();
    private Map<String, List<MockContentRow>> content = new HashMap<>();
    private int counter = 1;
    
    /**
     * @see io.apicurio.hub.api.storage.IStorage#getLinkedAccount(java.lang.String, io.apicurio.hub.api.beans.LinkedAccountType)
     */
    @Override
    public LinkedAccount getLinkedAccount(String userId, LinkedAccountType type)
            throws StorageException, NotFoundException {
        if (!accounts.containsKey(userId)) {
            throw new NotFoundException();
        }
        if (!accounts.get(userId).containsKey(type)) {
            throw new NotFoundException();
        }
        return accounts.get(userId).get(type);
    }
    
    /**
     * @see io.apicurio.hub.api.storage.IStorage#updateLinkedAccount(java.lang.String, io.apicurio.hub.api.beans.LinkedAccount)
     */
    @Override
    public void updateLinkedAccount(String userId, LinkedAccount account) throws NotFoundException, StorageException {
        this.getLinkedAccount(userId, account.getType()).setUsedOn(account.getUsedOn());
    }
    
    /**
     * @see io.apicurio.hub.api.storage.IStorage#createLinkedAccount(java.lang.String, io.apicurio.hub.api.beans.LinkedAccount)
     */
    @Override
    public void createLinkedAccount(String userId, LinkedAccount account)
            throws AlreadyExistsException, StorageException {
        if (!accounts.containsKey(userId)) {
            accounts.put(userId, new HashMap<>());
        }
        if (accounts.get(userId).containsKey(account.getType())) {
            throw new AlreadyExistsException();
        }
        accounts.get(userId).put(account.getType(), account);
    }
    
    /**
     * @see io.apicurio.hub.api.storage.IStorage#deleteLinkedAccount(java.lang.String, io.apicurio.hub.api.beans.LinkedAccountType)
     */
    @Override
    public void deleteLinkedAccount(String userId, LinkedAccountType type)
            throws StorageException, NotFoundException {
        if (!accounts.containsKey(userId)) {
            throw new NotFoundException();
        }
        if (!accounts.get(userId).containsKey(type)) {
            throw new NotFoundException();
        }
        accounts.get(userId).remove(type);
    }
    
    /**
     * @see io.apicurio.hub.api.storage.IStorage#deleteLinkedAccounts(java.lang.String)
     */
    @Override
    public void deleteLinkedAccounts(String userId) throws StorageException {
        this.accounts.remove(userId);
    }
    
    /**
     * @see io.apicurio.hub.api.storage.IStorage#listLinkedAccounts(java.lang.String)
     */
    @SuppressWarnings("unchecked")
    @Override
    public Collection<LinkedAccount> listLinkedAccounts(String userId) throws StorageException {
        if (!accounts.containsKey(userId)) {
            return Collections.EMPTY_LIST;
        }
        return accounts.get(userId).values();
    }

    /**
     * @see io.apicurio.hub.api.storage.IStorage#getApiDesign(java.lang.String, java.lang.String)
     */
    @Override
    public ApiDesign getApiDesign(String userId, String designId) throws NotFoundException, StorageException {
        ApiDesign design = this.designs.get(designId);
        if (design == null) {
            throw new NotFoundException();
        }
        return design;
    }
    
    /**
     * @see io.apicurio.hub.api.storage.IStorage#getCollaborators(java.lang.String, java.lang.String)
     */
    @Override
    public Collection<Collaborator> getCollaborators(String user, String designId)
            throws NotFoundException, StorageException {
        
        List<MockContentRow> list = this.content.get(designId);
        if (list == null || list.isEmpty()) {
            return Collections.emptySet();
        }
        
        Map<String, Integer> collabCounters = new HashMap<>();
        for (MockContentRow row : list) {
            if (row.designId.equals(designId)) {
                Integer editCount = collabCounters.get(row.createdBy);
                if (editCount == null) {
                    editCount = new Integer(0);
                }
                editCount = new Integer(editCount.intValue() + 1);
                collabCounters.put(row.createdBy, editCount);
            }
        }
        
        List<Collaborator> rval = new ArrayList<>();
        for (Entry<String, Integer> entry : collabCounters.entrySet()) {
            String collabUser = entry.getKey();
            int counter = entry.getValue();
            Collaborator collaborator = new Collaborator();
            collaborator.setName(collabUser);
            collaborator.setEdits(counter);
            rval.add(collaborator);
        }
        return rval;
    }
    
    /**
     * @see io.apicurio.hub.api.storage.IStorage#getLatestContentDocument(java.lang.String, java.lang.String)
     */
    @Override
    public ApiDesignContent getLatestContentDocument(String userId, String designId)
            throws NotFoundException, StorageException {
        List<MockContentRow> list = this.content.get(designId);
        if (list == null || list.isEmpty()) {
            throw new NotFoundException();
        }
        
        MockContentRow found = null;
        for (MockContentRow row : list) {
            if (row.designId.equals(designId) && row.type == ApiContentType.Document) {
                found = row;
            }
        }
        
        ApiDesignContent rval = new ApiDesignContent();
        rval.setContentVersion(found.version);
        rval.setOaiDocument(found.data);
        return rval;
    }
    
    /**
     * @see io.apicurio.hub.api.storage.IStorage#getContentCommands(java.lang.String, java.lang.String, long)
     */
    @Override
    public List<ApiDesignCommand> getContentCommands(String user, String designId, long sinceVersion)
            throws StorageException {
        List<ApiDesignCommand> rval = new ArrayList<>();

        List<MockContentRow> list = this.content.get(designId);
        if (list == null || list.isEmpty()) {
            return rval;
        }

        for (MockContentRow row : list) {
            if (row.designId.equals(designId) && row.type == ApiContentType.Command) {
                ApiDesignCommand cmd = new ApiDesignCommand();
                cmd.setContentVersion(row.version);
                cmd.setCommand(row.data);
            }
        }
        
        return rval;
    }
    
    /**
     * @see io.apicurio.hub.api.storage.IStorage#addContent(java.lang.String, java.lang.String, io.apicurio.hub.api.beans.ApiContentType, java.lang.String)
     */
    @Override
    public long addContent(String user, String designId, ApiContentType type, String data) throws StorageException {
        MockContentRow row = new MockContentRow();
        row.createdBy = user;
        row.data = data;
        row.type = type;
        row.designId = designId;
        this.addContentRow(designId, row);
        return row.version;
    }

    /**
     * @see io.apicurio.hub.api.storage.IStorage#createApiDesign(java.lang.String, io.apicurio.hub.api.beans.ApiDesign, java.lang.String)
     */
    @Override
    public String createApiDesign(String userId, ApiDesign design, String initialContent) throws StorageException {
        String designId = String.valueOf(counter++);
        design.setId(designId);
        this.designs.put(designId, design);
        
        MockContentRow contentRow = new MockContentRow();
        contentRow.designId = designId;
        contentRow.type = ApiContentType.Document;
        contentRow.data = initialContent;
        contentRow.createdBy = userId;
        this.addContentRow(designId, contentRow);
        
        return designId;
    }

    /**
     * @see io.apicurio.hub.api.storage.IStorage#deleteApiDesign(java.lang.String, java.lang.String)
     */
    @Override
    public void deleteApiDesign(String userId, String designId) throws NotFoundException, StorageException {
        if (this.designs.remove(designId) == null) {
            throw new NotFoundException();
        }
    }

    /**
     * @see io.apicurio.hub.api.storage.IStorage#updateApiDesign(java.lang.String, io.apicurio.hub.api.beans.ApiDesign)
     */
    @Override
    public void updateApiDesign(String userId, ApiDesign design) throws NotFoundException, StorageException {
        ApiDesign savedDesign = this.getApiDesign(userId, design.getId());
        savedDesign.setName(design.getName());
        savedDesign.setDescription(design.getDescription());
    }

    /**
     * @see io.apicurio.hub.api.storage.IStorage#listApiDesigns(java.lang.String)
     */
    @Override
    public Collection<ApiDesign> listApiDesigns(String userId) throws StorageException {
        return this.designs.values();
    }
    
    /**
     * Adds a content row.
     * @param designId
     * @param row
     */
    private void addContentRow(String designId, MockContentRow row) {
        List<MockContentRow> list = this.content.get(designId);
        if (list == null) {
            list = new ArrayList<>();
            this.content.put(designId, list);
        }
        list.add(row);
    }
    
    public static class MockContentRow {
        private static long CONTENT_COUNTER = 0;
        
        //CREATE TABLE api_content (design_id BIGINT NOT NULL, version BIGINT AUTO_INCREMENT NOT NULL, type TINYINT NOT NULL, data CLOB NOT NULL, created_by VARCHAR(255) NOT NULL, created_on TIMESTAMP NOT NULL);
        public String designId;
        final public long version = CONTENT_COUNTER++;
        public ApiContentType type = ApiContentType.Document;
        public String data;
        public String createdBy;
        final public Date createdOn = new Date();
        
    }

}
