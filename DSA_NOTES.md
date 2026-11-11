# DSA Notes (viva ke liye)

## 1. Stack kaise bana hai (CustomStack.java)
- Array `Batch[] data` + integer `top`. Koi `java.util.Stack`, `ArrayDeque` ya `LinkedList` use nahi hua.
- Khali stack: `top = -1`. Full stack: `top = capacity - 1`.

## 2. Time complexity
| Operation | Kya hota hai | Time | Kyun |
|-----------|--------------|------|------|
| PUSH (Stock In) | `top++`, `data[top] = batch` | O(1) | Sirf ek index badalta hai, koi loop nahi |
| POP (Stock Out) | `batch = data[top]`, `top--` | O(1) | Sirf TOP ka element nikalta hai |
| PEEK | `data[top]` read | O(1) | TOP badalta nahi |
| isEmpty / isFull | `top` ko compare karna | O(1) | |
| Search batch | sab batches POP karke helper stack mein, phir wapas PUSH | O(n) | Har batch ek baar dekha jaata hai |
| Delete product ke batches | search jaisa hi, matching batches skip | O(n) | Baaki ka order same rehta hai |

Space complexity: O(capacity) (fixed array). Search/delete mein helper stack ke liye extra O(n).

Database ka kharcha (INSERT/DELETE ek row) alag hai; stack operation khud O(1) hai.

## 3. LIFO Stock Out
`ProductManager.popBatch()` hamesha `stack.pop()` se TOP (latest) batch nikalta hai. Beech ya neeche ka batch seedha
nahi nikal sakte. Randomized test (300 PUSH/POP) mein har POP latest batch hi mila.

## 4. Database aur Stack ka sync
- Memory ka stack aur MySQL table `batches` (column `position`: 0 = BOTTOM) hamesha same rehte hain.
- PUSH/POP ek **transaction** mein hota hai: batch row + product quantity + history. Error aaye to `rollback`
  aur memory stack wapas pehle jaisa (undo pop/push).
- App restart par stack `batches` table se `ORDER BY position` se wapas banta hai.
- Product quantity = us product ke saare batches ka sum (quantity sirf PUSH/POP se badalti hai).

## 5. CRUD
| Entity | Create | Read / Search | Update | Delete |
|--------|--------|---------------|--------|--------|
| Product | Add Product | Inventory (search, filter, sort) | Edit dialog | Delete (Admin) |
| Category | Add | Categories page | Rename / description | Delete (agar products na hon) |
| Batch | PUSH | Batches, Stack search | Date / supplier edit | POP (sirf TOP) |
| User | Add User | Users page | Role, password reset | Delete (last admin nahi) |
