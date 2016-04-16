package tweedledee

import groovy.time.TimeCategory
import grails.rest.RestfulController

class AccountController extends RestfulController<Account> {
    
    static responseFormats = ['json', 'xml']

    //def passwordEncoder

    AccountController() {
        super(Account)
    }

    @Override
    def index(final Integer max) {
        params.max = Math.min(max?:10,100)
        if(params.id || params.handle) _handleParams(params)
        else {
            if(params.format){
                def accts=Account.list(params)
                respond accts, model:[accoutCount: Account.count()]
            } else _respondError(422,"Invalid account id")
        }
    }

    @Override
    def show(){
        if(params.id) _handleParams(params)
        else _respondError(404,"No account found")    
    }

    def addFollower(){
        if(!params.accountId || !params.followerId) _respondError(422,"Invalid account")
        def accountID=_handleAccountId(params.accountId)
        def followerID=_handleAccountId(params.followerId)
        if(accountID==followerID) _respondError(422,"Duplicate account")
        if(followerID){
            def followerAcct=Account.get(followerID)
            if(followerAcct){
                def thisAccount=Account.get(accountID)
                if(thisAccount){
                    followerAcct.followers.add(thisAccount)
                    followerAcct.save()
                    thisAccount.following.add(followerAcct)
                    thisAccount.save()
                    _showAccountWithCounts(thisAccount)
                }
            } else _respondError(404,"No account found for follower") 
        } else _respondError(422,"No follower") 
    }

    def showFollowers(final Integer max,final Integer offset){
        
        _fetchFollow(params,max,offset,"followers")

    }

    def showFollowing(final Integer max,final Integer offset){

         _fetchFollow(params,max,offset,"following")

    }

    def showFeed(final Integer max,final Integer offset){
        def limit=Math.min(max?:10,100)
        def os=(offset) ? Math.min(offset,100) : 0
        def dt=(params.date) ? Date.parse("MM/dd/yyyy",params.date) : _formatDate(0,true)
        def accountID=_handleAccountId(params.accountId)
        if(accountID){
            def acct=Account.get(accountID)
            if(acct){
                def ff=Account.where { id in acct.followers.id }.list()
                if(ff) {
                    def yy=ff.id.join(",")
                    def q="select m.text,m.dateCreated,m.account.handle as account from Message m " +
                            "where m.dateCreated >= ? "+
                            "and m.account.id in (select a.id from Account a where a.id in ($yy)) "+
                            "order by dateCreated desc"
                    def allMesgs=Message.executeQuery(q,[dt],[max:limit])
                    respond count:allMesgs.size(),messages:allMesgs,date:dt.toString()
                } else {
                    response.status=200
                    respond ff
                }

            } else _respondError(422,"No invalid account")
        } else _respondError(404,"No account")
    }

    private _fetchFollow(params,max,offset,galf){
        
        def aa=false
        def limit=Math.min(max?:10,100)
        def os=(offset) ? Math.min(offset,100) : 0
        def accountID=_handleAccountId(params.accountId)
        def acct=Account.get(accountID)
        if(acct) {
            if(galf=='followers') aa=Account.where { id in acct.followers.id }.list([max:limit,offset:os])
            else aa=Account.where { id in acct.following.id }.list([max:limit,offset:os])
            def ff=[]
            aa.each(){ ff.add(id:it.id,name:it.name,handle:it.handle,email:it.email) }
            def kk=(galf=='followers') ? "followerCount" : "followingCount"
            respond "$galf":ff,"$kk":ff.size()
        } else _respondError(404,"No account found")
    
    }

    private _handleAccountId(accountID){
        
        def id=accountID
        def isNum = (id as String).isNumber()
        if(!isNum){
            def acct=Account.findByHandle(accountID)
            if(acct) return acct.id
            else _respondError(422,"Invalid account")
        }else if(accountID){
            return accountID
        }else {
            _respondError(422,"No account")
        }

    }

    private _handleParams(Map params){
        
        def isNum = (params.id) ? (params.id as String).isNumber() : false
        def acct=null
        if(params.id && isNum) acct=Account.get(params.id)
        else if(params.id && !isNum) acct=Account.findByHandle(params.id)
        else if(params.handle) acct=Account.findByHandle(params.handle)
        
        if(acct) _showAccountWithCounts(acct)
        else _respondError(404,"No account found")

    }

    private _showAccountWithCounts(acct){
        if(acct){
            def resp=[:]
            resp.put('id',acct.id)
            resp.put('name',acct.name)
            resp.put('email',acct.email)
            resp.put('handle',acct.handle)
            resp.put('messageCount',acct.messages.size())
            resp.put('followerCount',acct.followers.size())
            resp.put('followingCount',acct.following.size())
            resp.put('following', acct.following)
            resp.put('followers', acct.followers)
            respond resp
        } else _respondError(404,"No account found")
    }
    
    private _formatMessage(mesg){
        if(mesg){
            def resp=[:]
            def cd=new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(mesg.dateCreated)
            resp.put('id',mesg.id)
            resp.put('text',mesg.text)
            resp.put('dateCreated',cd)
            resp.put('account',mesg.account.handle)
            return resp
        } else _respondError(404,"No account found")
    }

    private _formatDate(undate,shortDate=false){
        def ep=Calendar.getInstance(TimeZone.getTimeZone('CST'))
        ep.setTimeInMillis(undate as Long)
        return (shortDate) ? ep.getTime() : ep.format("MM/dd/yyyy HH:mm:ss zzz")
    }

    private _respondError(code,mesg){
        response.status=code
        respond error:code,message:"$mesg"
    }

}